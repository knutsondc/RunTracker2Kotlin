package com.dknutsonlaw.android.runtracker2kotlin.ui

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.dknutsonlaw.android.runtracker2kotlin.Constants
import com.dknutsonlaw.android.runtracker2kotlin.R

class DialogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "Reached DialogActivity onCreate.")
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_dialog)
        val r = resources
        val errorCode = intent.getIntExtra(Constants.EXTRA_ERROR_CODE, -1)
        val textView = findViewById<TextView>(R.id.error_textview)
        textView.text = r.getString(R.string.error_number, errorCode)
        val button = findViewById<Button>(R.id.ok_button)
        button.setOnClickListener { _ -> finish() }
    }

    companion object {

        private val TAG = "DialogActivity"
    }

}
