package com.dknutsonlaw.android.runtracker2kotlin

/*
 *This Activity has no visible UI of its own. Its exclusive use is to provide its onActivityResult()
 *method when startResolutionForResult() needs to be called to get needed Location Settings fixed.
 */

import android.app.Activity
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast

class HeadlessActivity : AppCompatActivity() {

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        Log.i(TAG, "Reached onActivityResult() in headlessActivity in RunTracker2Kotlin")
        Log.i(TAG, "requestCode is " + requestCode)
        Log.i(TAG, "resultCode is " + resultCode)
        if (requestCode == Constants.LOCATION_SETTINGS_CHECK) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    RunTracker2Kotlin.locationSettingsState = true
                    Toast.makeText(this,
                            "All Location Settings requirements now met.",
                            Toast.LENGTH_LONG)
                            .show()
                }
                Activity.RESULT_CANCELED -> {
                    RunTracker2Kotlin.locationSettingsState = false
                    Toast.makeText(this, "You declined to enable Location Services.\n" + "Stopping tracking of this run.", Toast.LENGTH_LONG).show()

                    val run = RunManager.getRun(RunManager.currentRunId)
                    if (run!!.duration == 0L) {
                        Toast.makeText(this,
                                "Never got any locations for this Run. Deleting Run.",
                                Toast.LENGTH_LONG)
                                .show()
                        RunManager.deleteRun(RunManager.currentRunId)
                    }
                }
            }
        } else if (requestCode == Constants.MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                }
                Activity.RESULT_CANCELED -> {
                    Toast.makeText(this, "You canceled recovery of Google Play Services. " + "       Stopping Tracking.", Toast.LENGTH_LONG).show()
                    val run = RunManager.getRun(RunManager.currentRunId)
                    if (run!!.duration == 0L) {
                        Toast.makeText(this,
                                "Never got any locations for this Run. Deleting Run.",
                                Toast.LENGTH_LONG)
                                .show()
                        RunManager.deleteRun(RunManager.currentRunId)

                    }
                }
            }//sGoogleApiClient.connect();
        }
    }

    companion object {
        private val TAG = "HeadlessActivity"
    }
}


