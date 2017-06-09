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
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.here.android.mpa.common.*
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.venues3d.*
import java.lang.ref.WeakReference


class MainActivity : AppCompatActivity() {
    private val TAG: String = "KotlinDemo_MainActivity"
    private val REQUEST_CODE : Int = 48317
    private lateinit var mapFragment:VenueMapFragment
    private lateinit var map:Map
    private var mVenueCached: Boolean = false
    private var mVenueEnabled: Boolean = false

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
            map.setCenter(GeoCoordinate(49.196261, -123.004773, 0.0), Map.Animation.NONE)
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
                        mapFragment.venueService.addVenueLoadListener { venue, venueInfo, venueLoadStatus ->
                            if (venueLoadStatus == VenueService.VenueLoadStatus.FAILED) {
                                Log.d(TAG, "Loading venue failed!!")
                            } else {
                                Log.d(TAG, "Loading venue ID ${venue.id} has been completed")
                            }
                        }
                        mapFragment.addListener(object:VenueMapFragment.VenueListener {
                            override fun onVenueDeselected(p0: Venue?, p1: DeselectionSource?) {

                            }

                            override fun onFloorChanged(p0: Venue?, p1: Level?, p2: Level?) {

                            }

                            override fun onSpaceSelected(p0: Venue?, p1: Space?) {
                                Log.d(TAG, "onSpaceSelected")
                            }

                            override fun onSpaceDeselected(p0: Venue?, p1: Space?) {

                            }

                            override fun onVenueSelected(p0: Venue?) {
                                Log.d(TAG, "onVenueSelected")

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
                        })
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
                    Log.d(TAG, (map.getSelectedObjectsNearby(p0).size > 0).toString())

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
                    return true
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
            findViewById(R.id.set_departure).setOnClickListener {

            }
            findViewById(R.id.set_destination).setOnClickListener {

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



}
