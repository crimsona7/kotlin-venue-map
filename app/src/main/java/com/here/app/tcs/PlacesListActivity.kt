package com.here.app.tcs

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.search.*
import kotlinx.android.synthetic.main.activity_places_list.*
import java.util.*

class PlacesListActivity() : AppCompatActivity() {
    private val TAG: String = "PlacesListActivity"
    val mTextWatcher = object:TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            s?.let {
                if (it.isNotEmpty()) {
                    val request = TextAutoSuggestionRequest(it.toString())
                    if (!coord_lat.text.isNullOrEmpty() && !coord_lon.text.isNullOrEmpty()) {
                        val lat = coord_lat.text.toString().toDoubleOrNull()
                        val lon = coord_lon.text.toString().toDoubleOrNull()
                        if (lat != null && lon != null) {
                            request.setSearchCenter(GeoCoordinate(lat, lon))
                        }
                    } else if (mapCenter != null) {
                        request.setSearchCenter(mapCenter)
                    } else {
                        // Default: Chicago
                        request.setSearchCenter(GeoCoordinate(41.88415,-87.63189))
                    }

                    request.execute(mSuggestListener)
                }
            }
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

        }
    }

    val mSuggestListener = ResultListener<MutableList<AutoSuggest>> { list, errorCode ->
        if (errorCode != ErrorCode.NONE) {
            Log.e(TAG, "Error on ResultListener: ${errorCode.name}")
            return@ResultListener
        }
        list.forEach {
            if (it.type != AutoSuggest.Type.PLACE) {
                list.remove(it)
            }
        }
        mSuggestAdapter = SuggestAdapter(list,  this)
        mSuggestAdapter?.let {
            search_text_view.setAdapter(mSuggestAdapter)
        }

    }

    val mSuggestClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
        mSuggestAdapter?.getItem(position)?.let {
            var request :DiscoveryRequest? = null

        }
    }

    var mSuggestAdapter : SuggestAdapter? = null
    var mapCenter: GeoCoordinate? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_places_list)
        val mapLat = intent.getDoubleExtra("map_center_lat", Double.NaN)
        val mapLon = intent.getDoubleExtra("map_center_lon", Double.NaN)
        mapCenter = GeoCoordinate(mapLat, mapLon).takeIf {
            !mapLat.isNaN() && !mapLon.isNaN()
        }

        search_text_view.addTextChangedListener(mTextWatcher)
        search_text_view.onItemClickListener = mSuggestClickListener
        search_type_spinner.setSelection(0)

        locale_spinner.also {
            val localeNameList = ArrayList<String>()
            Locale.getAvailableLocales().forEachIndexed { index, locale ->
                localeNameList.add(index, locale.displayName)
            }

            it.adapter =
                    ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, localeNameList)
        }

    }
}
