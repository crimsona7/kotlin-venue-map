/*
    May 22 2017 Chris Won

 */

package com.here.app.tcs

import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.PointF
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.MenuItemCompat
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.here.android.mpa.common.*
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.routing.UMRouter
import com.here.android.mpa.search.Category
import com.here.android.mpa.venues3d.*
import com.here.android.mpa.venues3d.Space
import java.lang.ref.WeakReference



class MainActivity : AppCompatActivity() {
    private val TAG: String = "KotlinDemo_MainActivity"
    private val REQUEST_CODE : Int = 48317
    private lateinit var mapFragment:VenueMapFragment
    private lateinit var map:Map
    private val mActivity = this
    private var mVenueCached: Boolean = false
    private var mVenueEnabled: Boolean = false
    private var mSpinner: Spinner? = null
    private var mAdapter: ArrayAdapter<CharSequence>? = null
    private val mMapPropBackup: MapProperties by lazy {
        MapProperties()
    }
    private lateinit var startLocation : BaseLocation
    private lateinit var endLocation: BaseLocation





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
            var errorMessage: String = error.details
            Log.e(TAG, "Error on init: $errorMessage")
            finish()
        } else {
            map = mapFragment.map ?: return@OnEngineInitListener
            map.setCenter(GeoCoordinate(40.74979,-73.98779, 0.0), Map.Animation.NONE)
            map.setZoomLevel((map.maxZoomLevel + map.minZoomLevel) /  2, Map.Animation.NONE)
            map.setCartoMarkersVisible(IconCategory.ALL, true)
            map.projectionMode = Map.Projection.GLOBE


            mapFragment.mapGesture.addOnGestureListener(mGestureListener,1000, true)
            mapFragment.mapGesture.setAllGesturesEnabled(true)

            LocationDataSourceHERE.getInstance()?.let {
                val pm: PositioningManager = PositioningManager.getInstance()
                pm.dataSource = it
                pm.addListener(WeakReference(mPositionListener))
                pm.start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR).also {
                    if (!it) {
                        Log.e(TAG, "PostioningManager: Start failed")
                    }
                }
                it.indoorPositioningMode = LocationDataSourceHERE.IndoorPositioningMode.AUTOMATIC
            }

        }
    }

    val mVenueListener: VenueMapFragment.VenueListener = object: VenueMapFragment.VenueListener {
        override fun onVenueDeselected(p0: Venue?, p1: DeselectionSource?) {
            mAdapter?.clear()
            mMapPropBackup.run {
                if (isValid) {
                    map.setCenter(geocoord, Map.Animation.BOW, zoom, orientation, tilt)
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
                mBottomSheetDialog.also {  dialog ->
                    val name  = dialog.findViewById(R.id.poi_name) as TextView
                    val address = dialog.findViewById(R.id.poi_address) as TextView
                    val category = dialog.findViewById(R.id.poi_category) as TextView
                    val phone = dialog.findViewById(R.id.poi_phone_number) as TextView
                    name.text = p1.content.name
                    address.text = p1.content.address.toString()
                    phone.text = p1.content.phoneNumber
                    Log.d(TAG, "Cat ID: ${p1.content.placeCategoryId}")
                    Category.globalCategories()?.forEach  {
                        if (it.id == p1.content.placeCategoryId) {
                            category.text = it.name
                            return@forEach
                        } else {
                            it.subCategories?.let { sub1 ->
                                sub1.forEach { sub1Item ->
                                    if (sub1Item.id == p1.content.placeCategoryId) {
                                        category.text = sub1Item.name
                                        return@forEach
                                    } else {
                                        it.subCategories?.let { sub2 ->
                                            sub2.forEach { sub2Item ->
                                                if (sub2Item.id == p1.content.placeCategoryId)
                                                {
                                                    category.text = sub2Item.name
                                                    return@forEach
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }

                    dialog.findViewById(R.id.set_departure).setOnClickListener {
                        startLocation = loc

                    }
                    dialog.findViewById(R.id.set_destination).setOnClickListener {
                        endLocation = loc
                    }
                }
                mBottomSheetDialog.show()
            }

        }

        override fun onSpaceDeselected(p0: Venue?, p1: Space?) {

        }

        override fun onVenueSelected(p0: Venue?) {
            Log.d(TAG, "onVenueSelected")

            p0?.let {
                mMapPropBackup.run {
                    geocoord = map.center
                    zoom = map.zoomLevel
                    orientation = map.orientation
                    tilt = map.tilt
                }
                map.zoomTo(it.boundingBox,Map.Animation.BOW, map.orientation, 60.0F)
                mAdapter = ArrayAdapter<CharSequence>(mActivity,
                        android.R.layout.simple_dropdown_item_1line).takeIf {  mAdapter == null  }
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
                    it.adapter = mAdapter.takeIf {  _ ->  it.adapter == null }
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

                    }
                }

                override fun onGetVenueCompleted(p0: Venue?) {

                }
            }

    val mGestureListener: MapGesture.OnGestureListener.OnGestureListenerAdapter =
            object: MapGesture.OnGestureListener.OnGestureListenerAdapter() {
                override fun onLongPressEvent(p0: PointF?): Boolean {
                    val coordinate:GeoCoordinate = map.pixelToGeo(p0)
                    Log.d(TAG, "Long press")
//                    Log.d(TAG, (map.getSelectedObjectsNearby(p0).size > 0).toString())

//                    val request:HereRequest = HereRequest()
//                    request.connectivity = Request.Connectivity.DEFAULT
//                    request.collectionSize = 20
//                    request.setSearchCenter(coordinate)
//                    request.execute()

//                    val mapObjs :List<ViewObject> = map.getSelectedObjectsNearby(p0)
//                    if (mapObjs.isNotEmpty()) {
//                        mapObjs
//                                .filterIsInstance<MapProxyObject>()
//                                .forEach {
//                                    when(it.type) {
//                                        MapProxyObject.Type.MAP_CARTO_MARKER -> {
//                                            (it as MapCartoMarker).location.info.let { locInfo ->
//                                                val name:String? =
//                                                        locInfo.getField(LocationInfo.Field.PLACE_NAME)
//                                            }
//
//
//                                        }
//
//                                    }
//                                }
//
//                    }


                    return false
                }

                override fun onMapObjectsSelected(p0: MutableList<ViewObject>?): Boolean {
                    Log.d(TAG, "Selected")
                    val mapObjs = p0.orEmpty()
                    var name:String?
                    var address:String?
                    var phoneNum:String?
                    var category:String?
                    var coordinate:GeoCoordinate?
                    if (mapObjs.isNotEmpty()) {
                        mapObjs.filterIsInstance<MapProxyObject>()
                                .forEach {
                                    when (it.type) {
                                        MapProxyObject.Type.MAP_CARTO_MARKER -> {
                                            (it as MapCartoMarker).run {
                                                coordinate = location.coordinate
                                                location.info.run {
                                                    name = getField(LocationInfo.Field.PLACE_NAME)
                                                    category = getField(LocationInfo.Field.PLACE_CATEGORY)
                                                    phoneNum = getField(LocationInfo.Field.PLACE_PHONE_NUMBER)
                                                    address = getField(LocationInfo.Field.ADDR_COUNTRY_NAME) +
                                                            " " + getField(LocationInfo.Field.ADDR_CITY_NAME) +
                                                            " " + getField(LocationInfo.Field.ADDR_COUNTY_NAME) +
                                                            " " + getField(LocationInfo.Field.ADDR_DISTRICT_NAME) +
                                                            " " + getField(LocationInfo.Field.ADDR_HOUSE_NUMBER) +
                                                            " " + getField(LocationInfo.Field.ADDR_BUILDING_NAME) +
                                                            " " + getField(LocationInfo.Field.ADDR_POSTAL_CODE)

                                                    Log.d(TAG, "Name: " + name)
                                                    Log.d(TAG, "Category: " + category)
                                                    Log.d(TAG, "Phone Number: " + phoneNum)
                                                    Log.d(TAG, "Address: " + address)
                                                }
                                            }
                                        }  else  -> {
                                        name = ""
                                        category = ""
                                        phoneNum = ""
                                        address = ""
                                        coordinate = null

                                        }
                                    }
                                }
                    } else {
                        Log.d(TAG, "mapObj Empty")
                    }
                    return false
                }
                override fun onTapEvent(p0: PointF?): Boolean {
                    p0?.let {
                        val coordinate = map.pixelToGeo(it)
                        map.getSelectedObjectsNearby(it)
                                .forEach {
                                    Log.d(TAG, "BaseType: ${it.baseType}")
                                    if (it is SpatialObject) {
                                        Log.d(TAG, "Spatial Object")
                                    }

                                }
                    }
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
        // MapFragment object is not obtainable by layout property,
        // instead we need to call findFragmentById of Android. (~API 25)
        mapFragment = (fragmentManager.findFragmentById(R.id.map_fragment)) as VenueMapFragment
//        val bundle: Bundle =
//                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
//        Log.d(TAG, "App ID: ${bundle.getString("com.here.android.maps.appid")}")
//        Log.d(TAG, "App Token: ${bundle.getString("com.here.android.maps.apptoken")}")
//        Log.d(TAG, "App License key: ${bundle.getString("com.here.android.maps.license.key")}")
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
        requestMissingPermissions()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        if (!MapEngine.isInitialized()) {
            return
        }
        outState?.let {
            val centerCoord = map.center
            outState.putDouble("map_latitude", centerCoord.latitude)
            outState.putDouble("map_longitude", centerCoord.longitude)
            outState.putDouble("map_zoomlevel", map.zoomLevel)
            outState.putFloat("map_orientation", map.orientation)
            outState.putFloat("map_tilt", map.tilt)

        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        if (!MapEngine.isInitialized()) {
            return
        }
        savedInstanceState?.let {
            var centerCoord:GeoCoordinate = GeoCoordinate(
                    it.getDouble("map_latitude") ?: Double.NaN,
                    it.getDouble("map_longitude") ?: Double.NaN)
            map.orientation = it.getFloat("map_orientation") ?: Float.NaN
            map.tilt = it.getFloat("map_tilt") ?: Float.NaN
            map.zoomLevel = it.getDouble("map_zoomlevel") ?: 0.0
            map.setCenter(centerCoord, Map.Animation.NONE)

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
        if (mapFragment.isVenueLayerVisible) {
            mapFragment.deselectVenue()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let {
            menuInflater.inflate(R.menu.menu_layout, it)
            val item: MenuItem = it.findItem(R.id.level_spinner)
            mSpinner = MenuItemCompat.getActionView(item) as Spinner
            mSpinner?.onItemSelectedListener = mLevelClickListener
        }
        return super.onCreateOptionsMenu(menu)
    }
}
