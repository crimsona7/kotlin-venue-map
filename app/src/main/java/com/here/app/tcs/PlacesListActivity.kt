package com.here.app.tcs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.search.*
import kotlinx.android.synthetic.main.activity_places_list.*
import java.util.Locale


class PlacesListActivity() : AppCompatActivity() {
    private val PVID_ID_REF_NAME = com.here.android.mpa.search.Request.PVID_ID_REFERENCE_NAME
    private val VENUES_ID_REF_NAME = com.here.android.mpa.search.Request.VENUES_ID_REFERENCE_NAME
    private val VENUE_ID_REF_NAME= com.here.android.mpa.search.Request.VENUES_VENUE_ID_REFERENCE_NAME
    private val BUILDING_ID_REF_NAME = com.here.android.mpa.search.Request.BUILDING_ID_REFERENCE_NAME
    private val TAG: String = "PlacesListActivity"
    val mTextWatcher = object:TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            s?.let {
                if (it.isNotEmpty()) {
                    val request = TextAutoSuggestionRequest(it.toString())
                    request.setSearchCenter(center.invoke())
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
        val resultList = list.filter {
            autoSuggest ->
            if (autoSuggest.type == AutoSuggest.Type.PLACE) {
                return@filter true
            }
            return@filter false
        }

        mSuggestAdapter = SuggestAdapter(resultList.toMutableList(),  this)
        mSuggestAdapter?.let {
            search_text_view.setAdapter(mSuggestAdapter)
        }

    }

    @Suppress("IMPLICIT_CAST_TO_ANY")
    val mSuggestClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
        mSuggestAdapter?.getItem(position)?.let {
            initiateSearch(it)
        }
    }

    val mDiscoveryResultListener = ResultListener<DiscoveryResultPage> { page, error ->
        Log.d(TAG, "Discovery result listener")
        if (error != ErrorCode.NONE) {
            Log.e(TAG, "Error while retrieving discovery result.\n${error.name}")
            return@ResultListener
        }
        search_result_list.adapter = PlaceListAdapter(this, page.placeLinks, mOnItemClickListener)
    }

    val mOnItemClickListener = object : PlaceListAdapter.OnItemClickListener {
        override fun onItemClick(link: PlaceLink?) {
            link?.let {
                Intent().run {
                    action = "com.here.tcs.chriswon.PICK_LOCATION"
                    putExtra("places_id", link.id)
                    putExtra("places_pvid", link.getReference(PVID_ID_REF_NAME))
                    putExtra("places_building_id", link.getReference(BUILDING_ID_REF_NAME))
                    putExtra("places_facebook_id", link.getReference("facebook"))
                    putExtra("places_yelp_id", link.getReference("yelp"))
                    putExtra("places_tripadvisor_id", link.getReference("tripadvisor"))
                    putExtra("places_opentable_id", link.getReference("opentable"))
//                    putExtra("places_venues_id", link.getReference(VENUES_ID_REF_NAME))
                    putExtra("places_venue_id", link.getReference(VENUE_ID_REF_NAME))
                    setResult(Activity.RESULT_OK, this)
                    finish()
                }
            }
        }
    }

    val mEditorActionListener = TextView.OnEditorActionListener { _, actionId, _ ->
        Log.d(TAG, "Editor Action Listener")
        Log.d(TAG, "Action ID is $actionId")
        val text:Editable = search_text_view.text
        val imm: InputMethodManager
                = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            if (!text.isNullOrEmpty() && text.isNotBlank()) {
                initiateSearch(text.toString())
                imm.hideSoftInputFromWindow(search_text_view.windowToken, 0)
                search_text_view.clearFocus()
                return@OnEditorActionListener true
            }
        }
        return@OnEditorActionListener false
    }

    val mButtonClickListener:(View) -> Unit = {
        Log.d(TAG, "Button Click Listener")
        if (!search_text_view.text.isNullOrEmpty() && search_text_view.text.isNotBlank()) {
            initiateSearch(search_text_view.text.toString())
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

        with(search_text_view) {
            addTextChangedListener(mTextWatcher)
            onItemClickListener = mSuggestClickListener
            setOnEditorActionListener(mEditorActionListener)
        }

        search_type_spinner.setSelection(0)

        locale_spinner.also {
            val localeNameList = ArrayList<String>()
            Locale.getAvailableLocales().forEachIndexed { index, locale ->
                localeNameList.add(index, locale.displayName)
            }

            it.adapter =
                    ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, localeNameList)
        }

        category_spinner.also {
            val categoryList = Category.globalCategories()
        }
        DividerItemDecoration(search_result_list.context, RecyclerView.HORIZONTAL).run {
            search_result_list.addItemDecoration(this)
        }
        place_search_button.setOnClickListener(mButtonClickListener)

    }

    val center = fun () : GeoCoordinate {
        if (!coord_lat.text.isNullOrEmpty() && !coord_lon.text.isNullOrEmpty()) {
            val lat = coord_lat.text.toString().toDoubleOrNull()
            val lon = coord_lon.text.toString().toDoubleOrNull()
            if (lat != null && lon != null) {
                return GeoCoordinate(lat, lon)
            }
        }
        return if (mapCenter != null) {
            mapCenter as GeoCoordinate
        } else {
            GeoCoordinate(41.88415, -87.63189)
        }
    }
    private fun initiateSearch(query: String) {
        val searchCenter = center.invoke()
        when (search_type_spinner.selectedItem) {
            "SEARCH" -> {
                Log.d(TAG, "Search")
                SearchRequest(query).run {
                    addReference(PVID_ID_REF_NAME)
                    addReference(VENUES_ID_REF_NAME)
                    addReference(VENUE_ID_REF_NAME)
                    addReference(BUILDING_ID_REF_NAME)
                    addReference("facebook")
                    addReference("yelp")
                    addReference("tripadvisor")
                    addReference("opentable")
                    collectionSize = 30
                    setSearchCenter(searchCenter)
                    execute(mDiscoveryResultListener)
                }
            }
            "EXPLORE" -> {
                Log.d(TAG, "Explore")
                ExploreRequest().run {
                    addReference(PVID_ID_REF_NAME)
                    addReference(VENUES_ID_REF_NAME)
                    addReference(VENUE_ID_REF_NAME)
                    addReference(BUILDING_ID_REF_NAME)
                    addReference("facebook")
                    addReference("yelp")
                    addReference("tripadvisor")
                    addReference("opentable")
                    collectionSize = 30
                    setSearchCenter(searchCenter)
                    // TODO: Add category filter here
                    execute(mDiscoveryResultListener)
                }
            }
            "AROUND" -> {
                Log.d(TAG, "Around")
                AroundRequest().run {
                    addReference(PVID_ID_REF_NAME)
                    addReference(VENUES_ID_REF_NAME)
                    addReference(VENUE_ID_REF_NAME)
                    addReference(BUILDING_ID_REF_NAME)
                    addReference("facebook")
                    addReference("yelp")
                    addReference("tripadvisor")
                    addReference("opentable")
                    collectionSize = 30
                    setSearchCenter(searchCenter)
                    // TODO: Add category filter here
                    execute(mDiscoveryResultListener)
                }
            }
            "HERE" -> {
                Log.d(TAG, "Here")
                HereRequest().run {
                    addReference(PVID_ID_REF_NAME)
                    addReference(VENUES_ID_REF_NAME)
                    addReference(VENUE_ID_REF_NAME)
                    addReference(BUILDING_ID_REF_NAME)
                    addReference("facebook")
                    addReference("yelp")
                    addReference("tripadvisor")
                    addReference("opentable")
                    collectionSize = 30
                    setSearchCenter(searchCenter)
                    // TODO: Add category filter here
                    execute(mDiscoveryResultListener)
                }

            }
            else -> {
                Log.d(TAG, "No possible type")
            }

        }
    }
}
