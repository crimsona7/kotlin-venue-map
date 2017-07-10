package com.here.app.tcs

import android.app.Activity
import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.here.android.mpa.search.AutoSuggest
import java.util.zip.Inflater

/**
 * Created by chwon on 6/22/2017.
 */

class SuggestAdapter(list: MutableList<AutoSuggest>, activity: Activity) : BaseAdapter(), Filterable {
    var mList: MutableList<AutoSuggest> = list
    val mCallingActivity: Activity = activity

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val itemView = LayoutInflater
                .from(mCallingActivity).inflate(R.layout.suggest_item_layout, parent, false)
        (itemView.findViewById(R.id.suggest_poi_name))?.let {
            if (it is TextView) {
                it.text = mList[position].title
            }
        }
        return itemView
    }

    override fun getItem(position: Int): String {
        return mList[position].title
    }



    override fun getItemId(position: Int): Long {
        return mList[position].hashCode().toLong()
    }

    override fun getCount(): Int {
        return mList.size
    }

    override fun getFilter(): Filter {
        var filter:Filter = object:Filter(){
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val result = FilterResults()

                result.values= if (mList.size > 10)  mList  else mList.subList(0, 10)
                result.count = 10
                return result
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {

            }
        }
        return filter

    }
}