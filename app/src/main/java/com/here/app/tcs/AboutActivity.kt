package com.here.app.tcs

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import kotlinx.android.synthetic.main.activity_about.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        var reader: BufferedReader? = null
        try {
            reader =
                    BufferedReader(InputStreamReader(
                            resources.openRawResource(R.raw.license_text,
                                    TypedValue().apply {    this.type = TypedValue.TYPE_STRING  }),
                            "UTF-8"))
        } catch (ie: IOException) {
            Log.e("AboutActivity", ie.message)
        }
        reader?.let {
            about_text.text = it.readText()
        }

    }
}
