package com.here.app.tcs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.here.android.mpa.search.PlaceLink
import com.here.android.mpa.search.Request
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.properties.Delegates

/**
 * Created by chwon on 6/30/2017.
 */


class PlaceListAdapter(val context: Context,
                       val placeResults:List<PlaceLink>,
                       val listener: PlaceListAdapter.OnItemClickListener):
        RecyclerView.Adapter<PlaceListAdapter.PlaceViewHolder>() {
    class PlaceViewHolder(context: Context,
                          parent:ViewGroup?,
                          elementView: View = LayoutInflater
                                  .from(context)
                                  .inflate(R.layout.place_list_item_layout, parent,  false))
            : RecyclerView.ViewHolder(elementView) {
        var placeName = elementView.findViewById(R.id.place_item_name) as TextView
        var placeCategory = elementView.findViewById(R.id.place_item_category) as TextView
        var placeIcon = elementView.findViewById(R.id.place_item_image_src) as ImageView
    }

    interface OnItemClickListener {
        fun onItemClick(link: PlaceLink?)
    }


    override fun getItemCount(): Int {
        return placeResults.size
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): PlaceViewHolder {
        return PlaceViewHolder(context, parent)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder?, position: Int) {
        holder?.let { listItem ->
            placeResults[position].apply {
                if (checkShowItem(this)) {
                    listItem.placeName.text = title

                    Log.d("List", "Category is ${category.name}")
                    listItem.placeCategory.text = category.name
                    Log.d("List", "Icon URL is ${iconUrl}")
                    getBitmapFromUrl(iconUrl)?.let {
                        listItem.placeIcon.setImageBitmap(it)
                    }
                    listItem.itemView.setOnClickListener {
                        listener.onItemClick(this)
                    }
                }
            }
        }

    }
    private fun getBitmapFromUrl(urlString: String) : Bitmap? {
        var bitMap: Bitmap? = null
        try {
            Log.d("Bitmap", "URL is $urlString")
            val url : URL = URL(urlString)
            //TO-DO: Fix the connection run on Runnable thread
            url.openConnection()?.let {
                (it as HttpURLConnection).doInput = true
                it.connect()
                bitMap= BitmapFactory.decodeStream(it.inputStream)
            }

        } catch (exception:IOException) {
            Log.e("Bitmap", "Error getting category Icon.\n${exception.message}")
        }
        return bitMap
    }

    private fun checkShowItem(placeLink: PlaceLink): Boolean {
        if (placeLink.getReference(Request.PVID_ID_REFERENCE_NAME).isNotEmpty() ||
                placeLink.getReference(Request.VENUES_VENUE_ID_REFERENCE_NAME).isNotEmpty() ||
                placeLink.getReference("facebook").isNotEmpty() ||
                placeLink.getReference("yelp").isNotEmpty() ||
                placeLink.getReference("tripadvisor").isNotEmpty() ||
                placeLink.getReference("opentable").isNotEmpty()) {
            return true
        }
        return false
    }
}
