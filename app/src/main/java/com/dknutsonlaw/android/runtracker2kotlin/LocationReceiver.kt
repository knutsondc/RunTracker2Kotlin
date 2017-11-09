package com.dknutsonlaw.android.runtracker2kotlin

/*
  Created by dck on 9/6/15 for RunTracker2Kotlin.
  added by dck 1/15/2015 to original RunTracker program.
  A subclass of Broadcast Receiver to receive Location updates for use in updating the database,
  with separate instantiations to provide "live" updates directly to the UIs in RunFragment and
  RunMapFragment.

  2/12/2015
  No longer used for "live" UI updates after implementation of MyLocationListCursorLoader that
  supplies "live" updates from the database, so the only instance left is TrackingLocationReceiver
  that supplies Location updates to the database.
 */
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log

import com.google.android.gms.location.LocationResult

open class LocationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        /*This method is called when the BroadcastReceiver is receiving
         *an Intent broadcast. Extract the results and use them.
         */
        if (LocationResult.hasResult(intent)) {
            val locationResult = LocationResult.extractResult(intent)
            val locationList = locationResult.locations
            for (i in locationList.indices) {
                onLocationReceived(context, locationList[i])
            }

        }
    }

    //The next method should be overridden in any actual implementation.
    internal open fun onLocationReceived(context: Context, loc: Location) {
        Log.d(TAG, this.toString() + " Got location from " + loc.provider + ": "
                + loc.latitude + ", " + loc.longitude)
    }

    companion object {
        private val TAG = "LocationReceiver"
    }
}
