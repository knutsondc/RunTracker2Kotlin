/* Created on 8/30/17 by DCK. This extension of the Application class simply creates a
 * GoogleApiClient that can be shared throughout the app. This class also manages the client's
 * connection to Google Services.
 */
package com.dknutsonlaw.android.runtracker2kotlin

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

@SuppressLint("Registered")
class RunTracker2Kotlin : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "In onCreate() of RunTracker2Kotlin")
        instance = this
        prefs = getSharedPreferences(Constants.PREFS_FILE, Context.MODE_PRIVATE)
        /*This instance of RunManager is never used, but we need to create it so that the static
         *methods in its RunDataBaseHelper member are immediately accessible to create the opening
         *RunRecyclerListFragment.
         */
        val sRunManager = RunManager.get(applicationContext)
    }

    companion object {

        private val TAG = RunTracker2Kotlin::class.java.simpleName
        @get:Synchronized
        var instance: Context? = null
            private set
        var locationSettingsState = false
        var prefs: SharedPreferences? = null
            private set
    }
}