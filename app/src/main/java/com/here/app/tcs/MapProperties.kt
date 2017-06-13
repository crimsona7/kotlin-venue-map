package com.here.app.tcs

import com.here.android.mpa.common.GeoCoordinate

/**
 * Created by chwon on 6/13/2017.
 */
data class MapProperties(var geocoord: GeoCoordinate = GeoCoordinate(Double.NaN, Double.NaN),
                         var zoom:Double = Double.NaN,
                         var orientation:Float = Float.NaN,
                         var tilt:Float = Float.NaN) {
    var isValid : Boolean =
        (!(geocoord.latitude == Double.NaN &&
                geocoord.longitude == Double.NaN &&
                zoom == Double.NaN &&
                orientation == 0.0F &&
                tilt == Float.NaN))

}