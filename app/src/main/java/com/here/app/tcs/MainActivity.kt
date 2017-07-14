/*
    May 22 2017 Chris Won

 */

package com.here.app.tcs

import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.MenuItemCompat
import android.support.v7.widget.SearchView
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.here.android.mpa.common.*
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.routing.RouteOptions
import com.here.android.mpa.routing.UMRouter
import com.here.android.mpa.search.*
import com.here.android.mpa.search.Location
import com.here.android.mpa.venues3d.*
import com.here.android.mpa.venues3d.Space
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import com.squareup.picasso.Target
import com.squareup.picasso.Transformation
import java.lang.ref.WeakReference
import java.util.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.collections.ArrayList




class MainActivity : AppCompatActivity() {
    val REQUEST_CODE_SEARCH: Int = 33667
    private val TAG: String = "KotlinDemo_MainActivity"
    val PVID_ID_REF_NAME = com.here.android.mpa.search.Request.PVID_ID_REFERENCE_NAME
    val VENUES_ID_REF_NAME = com.here.android.mpa.search.Request.VENUES_ID_REFERENCE_NAME
    val VENUE_ID_REF_NAME= com.here.android.mpa.search.Request.VENUES_VENUE_ID_REFERENCE_NAME
    val BUILDING_ID_REF_NAME = com.here.android.mpa.search.Request.BUILDING_ID_REFERENCE_NAME
    private val REQUEST_CODE : Int = 48317
    private lateinit var mapFragment:VenueMapFragment
//    private lateinit var map:Map
    private var map: Map? = null
    private val mActivity = this
    private var mVenueCached: Boolean = false
    private var mVenueEnabled: Boolean = false
    private var mSpinner: Spinner? = null
    private var mAdapter: ArrayAdapter<CharSequence>? = null
    private val mMapPropBackup: MapProperties by lazy {
        MapProperties()
    }
    private var startLocation : BaseLocation? = null
    private var endLocation: BaseLocation? = null


    // Do not use anonymous object as a listener, like Java - basically in Kotlin
    // there's no implicit reference to the anonymous object. Means, it will be garbage collected.
    // See below links for the details.
    // https://discuss.kotlinlang.org/t/function-literals-and-reference-to-enclosing-class/416
    // https://kotlinlang.org/docs/reference/object-declarations.html

    val mPositionListener : PositioningManager.OnPositionChangedListener =
            object: PositioningManager.OnPositionChangedListener {
                override fun onPositionFixChanged(p0: PositioningManager.LocationMethod?,
                                                  p1: PositioningManager.LocationStatus?) {
                    if (p1 != PositioningManager.LocationStatus.AVAILABLE) {
                        Log.e(TAG, "Location is not valid...")
                    }
                }

                override fun onPositionUpdated(p0: PositioningManager.LocationMethod?,
                                               p1: GeoPosition?, p2: Boolean) {
                    p1?.let {
                        Log.d(TAG, "Position Updated- ${it.coordinate}, map ${if (p2) "matched" else "not matched"}")
                        Log.d(TAG, "Position Updated Speed -${it.speed}")
                    }
//                    map.setCenter(p1?.let {
//                        it.coordinate.takeIf { p2 == true }
//                    }, Map.Animation.NONE)
                }
            }


    // Listeners could be implemented in two different way, like Java:
    // 1. Implement anonymous object in MapFragment.init(OnEnigineInitListener listener)
    // 2. Create an OnEngineInitListener object

    // In this example, 2nd option has been used with lambda abbreviation.
    // (Because there is only one callback method)
    val mEngineListener : OnEngineInitListener = OnEngineInitListener { error ->
        if (error != OnEngineInitListener.Error.NONE) {
            val errorMessage: String = error.details
            Log.e(TAG, "Error on init: $errorMessage")
            finish()
        } else {
            map = mapFragment.map ?: return@OnEngineInitListener
            map?.let { map ->
                map.setCenter(GeoCoordinate(40.74979,-73.98779, 0.0), Map.Animation.NONE)
                map.setZoomLevel((map.maxZoomLevel + map.minZoomLevel) /  2, Map.Animation.NONE)
                map.setCartoMarkersVisible(IconCategory.ALL, true)
                map.projectionMode = Map.Projection.GLOBE
            }

            mapFragment.mapGesture.addOnGestureListener(mGestureListener,1000, true)
            mapFragment.mapGesture.setAllGesturesEnabled(true)

            LocationDataSourceHERE.getInstance()?.let {
                val pm: PositioningManager = PositioningManager.getInstance()
                pm.dataSource = it
                pm.addListener(WeakReference(mPositionListener))
                it.indoorPositioningMode = LocationDataSourceHERE.IndoorPositioningMode.AUTOMATIC
            }
        }
    }

    val mVenueListener: VenueMapFragment.VenueListener = object: VenueMapFragment.VenueListener {
        override fun onVenueDeselected(p0: Venue?, p1: DeselectionSource?) {
            mAdapter?.clear()
            mMapPropBackup.run {
                if (isValid) {
                    map?.setCenter(geocoord, Map.Animation.BOW, zoom, orientation, tilt)
                }
            }


        }

        override fun onFloorChanged(p0: Venue?, p1: Level?, p2: Level?) {

        }

        override fun onSpaceSelected(p0: Venue?, p1: Space?) {
            Log.d(TAG, "onSpaceSelected")
            if (p0 != null && p1 != null) {
                val vnControl = mapFragment.getVenueController(p0)
                val loc = SpaceLocation(p1, vnControl)
                val routeContainer = route_container
                mBottomSheetDialog.also {  dialog ->
                    setPlaceDialog(p1.content.name,
                                    p1.content.address.toString(),
                                    convertPlaceCategoryIdToName(Category.globalCategories(), p1.content.placeCategoryId),
                                    p1.content.phoneNumber)
                    Log.d(TAG, "Cat ID: ${p1.content.placeCategoryId}")
                    dialog.findViewById(R.id.set_departure).setOnClickListener {
                        if (routeContainer.visibility == View.INVISIBLE) {
                            routeContainer.visibility = View.VISIBLE
                        }
                        departure_name.text = p1.content.name
                        startLocation = loc
                        calculate_button.isEnabled = (startLocation != null)
                        mBottomSheetDialog.dismiss()
                    }
                    dialog.findViewById(R.id.set_destination).setOnClickListener {
                        if (routeContainer.visibility == View.INVISIBLE) {
                            routeContainer.visibility = View.VISIBLE
                        }
                        destination_name.text = p1.content.name
                        endLocation = loc

                        calculate_button.isEnabled = (endLocation != null)
                        mBottomSheetDialog.dismiss()

                    }
                }
                if (!mBottomSheetDialog.isShowing) {
                    mBottomSheetDialog.show()
                }
            }

        }

        override fun onSpaceDeselected(p0: Venue?, p1: Space?) {

        }

        override fun onVenueSelected(p0: Venue?) {
            Log.d(TAG, "onVenueSelected")
            if (map == null) {
                return
            }
            p0?.let {
                mMapPropBackup.run {
                    geocoord = map?.center ?: GeoCoordinate(Double.NaN, Double.NaN)
                    zoom = map?.zoomLevel ?: Double.NaN
                    orientation = map?.orientation ?: 0.0F
                    tilt = map?.tilt ?: Float.NaN
                }
                if (!it.boundingBox.contains(map?.boundingBox)) {
                    map?.zoomTo(it.boundingBox, Map.Animation.BOW, map?.orientation ?: 0.0F, 60.0F)
                }
                mAdapter = ArrayAdapter<CharSequence>(mActivity,
                        android.R.layout.simple_dropdown_item_1line).takeIf { mAdapter == null }
                mAdapter?.let {
                    if (!it.isEmpty) {
                        it.clear()
                    }
                }
                p0.levels.forEach {
                    Log.d(TAG, "Floor - ${it.floorSynonym}")
                    mAdapter?.add(it.floorSynonym)
                }

                mSpinner?.let {
                    it.adapter = mAdapter.takeIf { _ -> it.adapter == null }
                    it.invalidate()
                }
                mapFragment.getVenueController(it).useVenueZoom(true)
            }

        }

        override fun onVenueTapped(p0: Venue?, p1: Float, p2: Float) {
            Log.d(TAG, "onVenueTapped")
            mapFragment.selectVenue(p0)
        }

        override fun onVenueVisibleInViewport(p0: Venue?, p1: Boolean) {
            if (p1) {
                Log.d(TAG, "Visible")
            } else {
                Log.d(TAG, "Not visible")
            }

        }
    }

    val mVenueLoadListener: VenueService.VenueLoadListener
            = VenueService.VenueLoadListener { venue, venueInfo, venueLoadStatus ->
        if (venueLoadStatus == VenueService.VenueLoadStatus.FAILED) {
            Log.d(TAG, "Loading venue failed!!")
        } else {
            Log.d(TAG, "Loading venue ID ${venue.id} has been completed")
        }
    }

    val mVenueServiceListener: VenueService.VenueServiceListener =
            object: VenueService.VenueServiceListener {
                override fun onInitializationCompleted(initStatus: VenueService.InitStatus?) {
                    when (initStatus)  {
                        VenueService.InitStatus.ONLINE_SUCCESS -> {
                            mVenueEnabled = true
                            Log.d(TAG, "Venue init completed")

                        }
                        VenueService.InitStatus.OFFLINE_SUCCESS -> {
                            mVenueCached = true
                            mVenueEnabled = true
                            Log.d(TAG, "Venue init completed as offline")

                        }
                        else -> {
                            if (initStatus == VenueService.InitStatus.ONLINE_FAILED
                                    && mVenueCached) {
                                mVenueEnabled = true


                            } else {
                                Log.e(TAG, "Error!! ${initStatus.toString()}")
                            }

                        }
                    }
                    if (mVenueEnabled && MapEngine.isInitialized()) {
                        Log.d(TAG, "Venue initialized")
                        mapFragment.setVenuesInViewportCallback(true)
                        mapFragment.venueService.addVenueLoadListener(mVenueLoadListener)
                        mapFragment.addListener(mVenueListener)
                        mapFragment.routingController.addListener(mRoutingListener)

                    }
                }
                override fun onGetVenueCompleted(p0: Venue?) {

                }
            }

    val mGestureListener: MapGesture.OnGestureListener.OnGestureListenerAdapter =
            object: MapGesture.OnGestureListener.OnGestureListenerAdapter() {
                override fun onLongPressEvent(p0: PointF?): Boolean {
                    Log.d(TAG, "Long press")
                    p0?.let {
                        if (mapFragment.selectedVenue == null) {
                            if (map != null && map!!.getSelectedObjects(it).size <= 0) {
                                val coord = map?.pixelToGeo(it)
                                Log.d(TAG, "1")
                                ReverseGeocodeRequest2(coord, Locale.ENGLISH)
                                        .execute(mGeocodeListener)

                            }

                        }
                    }
                    return false
                }

                override fun onMapObjectsSelected(p0: MutableList<ViewObject>?): Boolean {
                    Log.d(TAG, "Selected")
                    p0?.let { list ->
                        if (!list.isEmpty()) {
                            list[0].also {
                                if (ViewObject.Type.PROXY_OBJECT == it.baseType
                                     && (it as MapProxyObject).type == MapProxyObject.Type.MAP_CARTO_MARKER) {
                                    val routeContainer = route_container
                                    val departureName = departure_name
                                    val destinationName = destination_name
                                    val calculateButton = calculate_button
                                    val loc = (it as MapCartoMarker).location
                                    loc.info?.let { info ->
                                        setPlaceDialog(
                                                info.getField(LocationInfo.Field.PLACE_NAME),
                                                getIntegratedAddress(info),
                                                info.getField(LocationInfo.Field.PLACE_CATEGORY),
                                                info.getField(LocationInfo.Field.PLACE_PHONE_NUMBER)
                                        )
                                        mBottomSheetDialog.run {
                                            findViewById(R.id.set_departure).setOnClickListener {
                                                if (routeContainer.visibility == View.INVISIBLE) {
                                                    routeContainer.visibility = View.VISIBLE
                                                }
                                                departureName.text = info.getField(LocationInfo.Field.PLACE_NAME)
                                                startLocation = OutdoorLocation(loc.coordinate)
                                                calculateButton.isEnabled = (endLocation != null)
                                                mBottomSheetDialog.dismiss()
                                            }
                                            findViewById(R.id.set_destination).setOnClickListener {
                                                if (routeContainer.visibility == View.INVISIBLE) {
                                                    routeContainer.visibility = View.VISIBLE
                                                }
                                                destinationName.text = info.getField(LocationInfo.Field.PLACE_NAME)
                                                endLocation = OutdoorLocation(loc.coordinate)
                                                calculateButton.isEnabled = (startLocation != null)
                                                mBottomSheetDialog.dismiss()
                                            }
                                            if (!isShowing) show()
                                        }

                                    }
                                }
                            }

                        }

                    }

                    return false
                }
                override fun onTapEvent(p0: PointF?): Boolean {
                    p0?.let {
                        map?.getSelectedObjects(it)
                    }
//                    p0?.let {
//                        val coordinate = map.pixelToGeo(it)
//                        map.getSelectedObjectsNearby(it)
//                                .forEach {
//                                    Log.d(TAG, "BaseType: ${it.baseType}")
//                                    if (it is SpatialObject) {
//                                        Log.d(TAG, "Spatial Object")
//                                    }
//
//                                }
//                    }
                    return super.onTapEvent(p0)
                }
            }

    val mLevelClickListener:AdapterView.OnItemSelectedListener =
            object: AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {
//            mAdapter?.let {
//                if (!it.isEmpty) {
//                    val venuController:VenueController?
//                        = mapFragment.getVenueController(mapFragment.selectedVenue)
//                    venuController?.selectedLevel?.floorSynonym?.let { ground ->
//                        parent?.setSelection(it.getPosition(ground))
//                    }
//                }
//            }

        }

        override fun onItemSelected(parent: AdapterView<*>?,
                                    view: View?,
                                    position: Int,
                                    id: Long) {
            mapFragment.selectedVenue?.let {
                it.levels.forEach { level ->
                    if (level.floorSynonym == mAdapter?.getItem(position)) {
                        mapFragment.getVenueController(it).selectLevel(level)
                    }
                }
            }
        }
    }

    val mRoutingListener = RoutingController.RoutingControllerListener { combinedRoute ->
        mapFragment.routingController?.showRoute(combinedRoute)
    }

    val mGeocodeListener = ResultListener<Location> { location, errorCode ->
        val coord = location.coordinate
        if (errorCode == ErrorCode.NONE) {
            Log.d(TAG, "2")
            val routeContainer = route_container
            val departureName = departure_name
            val destinationName = destination_name
            val calculateButton = calculate_button
            mBottomSheetDialog.run {
                setPlaceDialog(
                        coord.toString(),
                        location.address.text,
                        "",
                        ""
                )
                findViewById(R.id.set_departure).setOnClickListener {
                    if (routeContainer.visibility == View.INVISIBLE) {
                        routeContainer.visibility = View.VISIBLE
                    }
                    departureName.text = coord.toString()
                    startLocation = OutdoorLocation(coord)
                    calculateButton.isEnabled = (endLocation != null)
                    mBottomSheetDialog.dismiss()
                }
                findViewById(R.id.set_destination).setOnClickListener {
                    if (routeContainer.visibility == View.INVISIBLE) {
                        routeContainer.visibility = View.VISIBLE
                    }
                    destinationName.text = coord.toString()
                    endLocation = OutdoorLocation(coord)
                    calculateButton.isEnabled = (startLocation != null)
                    mBottomSheetDialog.dismiss()
                }
                if (!isShowing) show()

            }

        }
    }

    val mPlaceRequestListener = ResultListener<Place> { place, errorCode ->
        if (errorCode != ErrorCode.NONE) {
            return@ResultListener
        }
        place?.let {
            val routeContainer = route_container
            val departureName = departure_name
            val destinationName = destination_name
            val calculateButton = calculate_button
            map?.setCenter(place.location.coordinate, Map.Animation.BOW,
                    19.0, map?.orientation ?: 0.0F, map?.tilt ?: 0.0F)

            val markerImage:Image = Image()
            val picasso = Picasso.with(mActivity).load(place.categories[0].iconUrl)
            picasso.into(object: Target {
                override fun onPrepareLoad(p0: Drawable?) {

                }

                override fun onBitmapFailed(p0: Drawable?) {

                }

                override fun onBitmapLoaded(p0: Bitmap?, p1: Picasso.LoadedFrom?) {
                    markerImage.bitmap = p0
                    map?.addMapObject(MapMarker(place.location.coordinate, markerImage))
                }
            })

//            markerImage.bitmap =
//            map?.addMapObject(MapMarker(place.location.coordinate, markerImage))
            setPlaceDialog(
                    poiName = place.name,
                    poiAddress = place.location.address.toString(),
                    poiCategory = place.categories.joinToString {   category ->
                        return@joinToString category.name   },
                    poiPhone = place.contacts.joinToString { contactDetail ->
                        return@joinToString "${contactDetail.type}: ${contactDetail.value}"
                    })
            with(mBottomSheetDialog) {
                this.findViewById(R.id.set_departure).setOnClickListener {
                    if (routeContainer.visibility == View.INVISIBLE) {
                        routeContainer.visibility = View.VISIBLE
                    }
                    departureName.text = place.name
                    startLocation = OutdoorLocation(place.location.coordinate)
                    calculateButton.isEnabled = (startLocation != null)
                    mBottomSheetDialog.dismiss()
                }
                this.findViewById(R.id.set_destination).setOnClickListener {
                    if (routeContainer.visibility == View.INVISIBLE) {
                        routeContainer.visibility = View.VISIBLE
                    }
                    destinationName.text = place.name
                    endLocation = OutdoorLocation(place.location.coordinate)

                    calculateButton.isEnabled = (endLocation != null)
                    mBottomSheetDialog.dismiss()

                }
                if (!isShowing) show()
            }
        }
    }


    private lateinit var mBottomSheetDialog : Dialog

//    val mDiscoveryResultListener: ResultListener<DiscoveryResultPage> = ResultListener<DiscoveryResultPage> {
//        resultPage, errorCode ->
//        if (errorCode != ErrorCode.NONE) {
//            Log.e(TAG, "Error on parsing search request results");
//            return@ResultListener
//        } else {
//            if (resultPage.items.count() > 0) {
//
//            }
//        }
//
//
//    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapFragment = (fragmentManager.findFragmentById(R.id.map_fragment)) as VenueMapFragment
        mBottomSheetDialog = Dialog(this, R.style.MaterialDialogSheet).apply {
            setCancelable(true)
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM)
            setContentView(R.layout.dialog_layout)
            findViewById(R.id.close_detail_window).setOnClickListener {
                mBottomSheetDialog.dismiss()
            }
        }
        search_activity_button.setOnClickListener {
            if (MapEngine.isInitialized() && map != null) {
                val searchActivityIntent = Intent().run {
                    action = "com.here.chriswon.LAUNCH_SEARCH_ACTIVITY"
                    putExtra("map_center_lat", map!!.center.latitude)
                    putExtra("map_center_lon", map!!.center.longitude)
                }
                startActivityForResult(searchActivityIntent, REQUEST_CODE_SEARCH)
            }
        }

        requestMissingPermissions()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        if (!MapEngine.isInitialized()) {
            return
        }
        outState?.let {
            map?.let { map ->
                val centerCoord = map.center
                outState.putDouble("map_latitude", centerCoord.latitude)
                outState.putDouble("map_longitude", centerCoord.longitude)
                outState.putDouble("map_zoomlevel", map.zoomLevel)
                outState.putFloat("map_orientation", map.orientation)
                outState.putFloat("map_tilt", map.tilt)
                mapFragment.selectedVenue?.let {
                    outState.putString("map_venue_id", it.id)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (MapEngine.isInitialized()) {
            PositioningManager.getInstance()
                    .start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR)
        }
    }

    override fun onPause() {
        if (MapEngine.isInitialized()) {
            PositioningManager.getInstance().stop()
        }
        super.onPause()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        if (!MapEngine.isInitialized()) {
            return
        }
        savedInstanceState?.let {
            val centerCoord:GeoCoordinate = GeoCoordinate(
                    it.getDouble("map_latitude",Double.NaN ),
                    it.getDouble("map_longitude", Double.NaN))
            map?.orientation = it.getFloat("map_orientation", Float.NaN)
            map?.tilt = it.getFloat("map_tilt", Float.NaN)
            map?.zoomLevel = it.getDouble("map_zoomlevel", Double.NaN)
            map?.setCenter(centerCoord, Map.Animation.NONE)
            it.getString("map_venue_id")?.let { venueId ->
                mapFragment.run {
                    selectVenueAsync(venueId)
                }
            }

        }
    }

    fun requestMissingPermissions() {
        Log.d(TAG, "Requesting missing permissions")
        // missingPermissions must be mutable, to stack the permissions which is not granted yet.
        val missingPermissions : MutableList<String> = mutableListOf()
//        val permissionsRequired: Array<PermissionInfo>? =
//                packageManager
//                        .getPackageInfo(packageName,PackageManager.GET_PERMISSIONS).permissions
        val permissionRequired : List<String> = resources.getStringArray(R.array.permissions).asList()

        for (permission in permissionRequired) {

            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Adding $permission to missingPermissions")
                missingPermissions.add(permission)
            }
        }
        if (missingPermissions.size > 0) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_CODE)
        } else {
            mapFragment.init(mEngineListener, mVenueServiceListener)
        }

    }

    override fun onRequestPermissionsResult (requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        Log.d(TAG, "onRequestPermissionsResult")
        loop@ for (i:Int in 0..(permissions.size - 1)) {
            when (grantResults[i]) {
                PackageManager.PERMISSION_GRANTED -> continue@loop
                PackageManager.PERMISSION_DENIED -> {
                    Toast.makeText(this,
                            "Permission ${permissions[i]} is not granted...",
                            Toast.LENGTH_SHORT).show()
                    finish()
                    return  // return from onRequestPermissionsResult
                }
            }
        }

        mapFragment.init(this, mEngineListener, mVenueServiceListener)  // Init MapFragment
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onBackPressed() {
        if (mapFragment.selectedVenue != null) {
            mapFragment.deselectVenue()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let {
            menuInflater.inflate(R.menu.menu_layout, it)
            val spinnerItem: MenuItem = it.findItem(R.id.level_spinner)
            mSpinner = MenuItemCompat.getActionView(spinnerItem) as Spinner
            mSpinner?.onItemSelectedListener = mLevelClickListener
            mSpinner?.setBackgroundColor(Color.WHITE)
        }
        return super.onCreateOptionsMenu(menu)
    }

    fun onClickCalculateButton(v: View): Unit {
        val routeOption: VenueRouteOptions = VenueRouteOptions()
        routeOption.flagsVisible = true
        routeOption.iconsVisible = true
        routeOption.routeOptions =
                RouteOptions().setRouteCount(1)
                        .setRouteType(RouteOptions.Type.SHORTEST)
                        .setTransportMode(RouteOptions.TransportMode.PEDESTRIAN)
        routeOption.setCorridorsPreferred(true)
        routeOption.setElevatorsAllowed(true)
        routeOption.setElevatorsAllowed(true)
        routeOption.setEscalatorsAllowed(true)
        routeOption.setRampsAllowed(true)
        if (startLocation != null && endLocation != null &&
                startLocation?.isValid!! && endLocation?.isValid!!) {
            Log.d(TAG, "Routing")
            mapFragment.routingController
                    ?.calculateCombinedRoute(startLocation, endLocation, routeOption)
        }
    }

    fun setPlaceDialog(poiName: String,
                       poiAddress: String,
                       poiCategory: String,
                       poiPhone: String): Unit {
        mBottomSheetDialog.also { dialog ->
            val name = dialog.findViewById(R.id.poi_name) as TextView
            val address = dialog.findViewById(R.id.poi_address) as TextView
            val category = dialog.findViewById(R.id.poi_category) as TextView
            val phone = dialog.findViewById(R.id.poi_phone_number) as TextView

            name.text = poiName
            address.text = poiAddress
            category.text = poiCategory
            phone.text = poiPhone
        }
    }

    fun convertPlaceCategoryIdToName (catList: List<Category>?,placeCategoryId: String) : String {
        var catName: String = ""
        catList?.forEach  {
            if (it.id == placeCategoryId) {
                catName = it.name
            } else {
                catName = convertPlaceCategoryIdToName(it.subCategories, placeCategoryId)
            }
        }
        return catName
    }

    fun getIntegratedAddress(info: LocationInfo): String {
        val builder : StringBuilder = StringBuilder()
        builder.append(info.getField(LocationInfo.Field.ADDR_HOUSE_NUMBER))
        builder.append("\n")
        builder.append(info.getField(LocationInfo.Field.ADDR_STREET_NAME))
        builder.append(info.getField(LocationInfo.Field.ADDR_DISTRICT_NAME))
        builder.append("\n")
        builder.append(info.getField(LocationInfo.Field.ADDR_COUNTY_NAME))
        builder.append(info.getField(LocationInfo.Field.ADDR_CITY_NAME))
        builder.append("\n")
        builder.append(info.getField(LocationInfo.Field.ADDR_STATE_CODE))
        builder.append(info.getField(LocationInfo.Field.ADDR_COUNTRY_NAME))

        return builder.toString()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult: requestCode - $requestCode, resultCode - $resultCode")
        Log.d(TAG, "onActivityResult: Intent action - ${intent.action}")
        when (requestCode) {
            REQUEST_CODE_SEARCH -> {
                data?.let {
                    if (resultCode == RESULT_OK) {
                        val placeId = it.extras.getString("places_id", "")
                        val placePVID = Pair<String, String>(PVID_ID_REF_NAME,
                                it.extras.getString("places_pvid", ""))

                        val placeBuildingId = Pair<String, String>(BUILDING_ID_REF_NAME,
                                it.extras.getString("places_building_id", ""))
                        val placeFacebookId = Pair <String, String>("facebook",
                                it.extras.getString("places_facebook_id", ""))
                        val placeYelpId = Pair<String, String>("yelp",
                                it.extras.getString("places_yelp_id", ""))
                        val placeTAId = Pair<String, String>("tripadvisor",
                                it.extras.getString("places_tripadvisor_id", ""))
                        val placeOTId = Pair<String, String>("opentable",
                                it.extras.getString("places_opentable_id", ""))
                        val placeVenueId = Pair<String, String>(VENUE_ID_REF_NAME,
                                it.extras.getString("places_venue_id", ""))
                        Log.d(TAG, "onActivityResult: $placePVID, $placeBuildingId, $placeFacebookId, $placeYelpId, $placeTAId, $placeOTId, $placeVenueId, $placeId")
                        if (placeVenueId.second.isNotEmpty()) {
                            mapFragment.selectVenueAsync(placeVenueId.second)
                        } else {
                            val placeRequest: PlaceRequest? =
                            if (placePVID.second.isNotEmpty()) {
                                PlaceRequest(placePVID.first, placePVID.second)
                            } else if (placeBuildingId.second.isNotEmpty()) {
                                PlaceRequest(placeBuildingId.first, placeBuildingId.second)
                            } else if (placeFacebookId.second.isNotEmpty()){
                                PlaceRequest(placeFacebookId.first, placeFacebookId.second)
                            } else if (placeYelpId.second.isNotEmpty()){
                                PlaceRequest(placeYelpId.first, placeYelpId.second)
                            } else if (placeTAId.second.isNotEmpty()){
                                PlaceRequest(placeTAId.first, placeTAId.second)
                            } else if (placeOTId.second.isNotEmpty()) {
                                PlaceRequest(placeOTId.first, placeOTId.second)
                            } else {
                                null
                            }
                            if (placeRequest == null) {
                                Log.e(TAG, "PlaceRequest is null")
                            }
                            placeRequest?.execute(mPlaceRequestListener)

                        }
                    }
                }
            }
        }
    }

}
