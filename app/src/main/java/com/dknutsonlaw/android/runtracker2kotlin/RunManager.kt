package com.dknutsonlaw.android.runtracker2kotlin

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.util.LongSparseArray
import android.text.TextUtils
import android.util.Log

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

import java.io.IOException
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Created by dck on 9/6/15. Basic set of methods for creating, updating and deleting Runs and their
 * constituent fields.
 */
class RunManager//The private constructor forces users to use RunManager.get(Context)
private constructor(appContext: Context) {
    private var mResultsReceiver: ResultsReceiver? = null

    init {
        //Use the application context to avoid leaking activities
        sAppContext = appContext.applicationContext
        /*//This reference to a RunDatabaseHelper isn't used directly, but is needed to make static
         *methods in that class immediately available.
        */
        //val helper = RunDatabaseHelper(appContext)
    }

    internal fun startTrackingRun(runId: Long) {
        /*Location updates get started from the CombinedFragment by binding to the
         *BackgroundLocationService and instructing it to start supplying the updates. This method
         *handles the other housekeeping associated with starting to track a run. We get here from
         *the CombinedFragment's Start Button.
         */
        //First, keep the RunId in a member variable.
        currentRunId = runId
        //Store it in shared preferences to make it available throughout the app.
        RunTracker2Kotlin.prefs!!.edit().putLong(Constants.PREF_CURRENT_RUN_ID, runId).apply()
        Log.i(TAG, "Run $runId saved in SharedPrefs as PREF_CURRENT_RUN_ID")
        /*Now that location updates have started, RunManager needs to listen for broadcasts directing
         *that End Address updates start or stop, so we register a BroadcastReceiver
         */
        mResultsReceiver = ResultsReceiver()
        /*Filter to allow receipt of signals in broadcast receiver that End Address updates should
         *start or end.
         */
        val intentFilter = IntentFilter(Constants.ACTION_START_UPDATING_END_ADDRESS)
        intentFilter.addAction(Constants.ACTION_STOP_UPDATING_END_ADDRESS)
        LocalBroadcastManager.getInstance(sAppContext!!).registerReceiver(mResultsReceiver!!, intentFilter)
    }

    /*The logic in this method moved to onSuccessListener()of removeLocationUpdates()in
     *stopLocationUpdates method of BackgroundLocationService. If no Run is being tracked, no
     *broadcast intents intended for the RunManager will be sent, so we can unregister the
     *broadcast receiver.
     */
    private fun stopTrackingRun() {
        Log.i(TAG, "Entered stopRun() in RunManager")
        //stopTrackingRun();
        LocalBroadcastManager.getInstance(sAppContext!!).unregisterReceiver(mResultsReceiver!!)
    }

    /*Insert a new Location into the database relating to the CurrentRun using the an
     *InsertLocationTask and Executor service to do so off the main, UI thread.
     */
    internal fun insertLocation(loc: Location) {
        //Log.d(TAG, "In RunManager insertLocation(), sCurrentRunId is: " + sCurrentRunId);
        if (currentRunId != -1L) {
            sExecutor.execute(InsertLocationTask(currentRunId, loc))
        } else {
            Log.e(TAG, "Location received with no tracking run; ignoring.")
        }
    }

    private inner class ResultsReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            val result = intent.action

            if (result != null) {
                when (result) {
                    Constants.ACTION_START_UPDATING_END_ADDRESS -> {
                        //Start recurring task of updating Ending Address
                        if (getRun(currentRunId) == null || currentRunId == -1L) {
                            return
                        }
                        sScheduledFuture = sExecutor.scheduleAtFixedRate(UpdateEndAddressTask(
                                getRun(currentRunId)),
                                20,
                                10,
                                TimeUnit.SECONDS)
                    }

                    Constants.ACTION_STOP_UPDATING_END_ADDRESS -> {
                        //Cancel the recurring updates of EndAddress when no longer tracking a Run.
                        if (sScheduledFuture != null) {
                            Log.d(TAG, "Reached ACTION_STOP_UPDATING_END_ADDRESS in RunManager" + " ResultsReceiver")
                            /*This interrupts the thread running the task so that an Exception gets
                             *thrown, thus stopping further execution of the task.
                            */
                            sScheduledFuture!!.cancel(true)
                        }
                        stopTrackingRun()
                    }
                    else -> Log.d(TAG, "You should never get here!!")
                }
            }
        }
    }

    /*The following private classes are Runnable tasks responsible for
     *doing all database accesses. They are executed by the Executor
     *service, so those tasks are all done on separate threads, not
     *UI thread.
     */
    private class InsertNewRunTask internal constructor(private val mRun: Run) : Runnable {
        override fun run() {
            /*Put the new Run's initial values into ContentValues and submit
             *them to the ContentResolver's insert method for the Run table..
             */
            val cv = ContentValues()
            cv.put(Constants.COLUMN_RUN_START_DATE, mRun.startDate!!.time)
            cv.put(Constants.COLUMN_RUN_START_ADDRESS, mRun.startAddress)
            cv.put(Constants.COLUMN_RUN_END_ADDRESS, mRun.endAddress)
            cv.put(Constants.COLUMN_RUN_DISTANCE, mRun.distance)
            cv.put(Constants.COLUMN_RUN_DURATION, mRun.duration)
            /*The ContentResolver's insertion of the Run into the Run table
             *returns the row number into which it was inserted. That number
             *becomes the Run's ID number, which is placed into the last path
             *segment of the URI returned by the ContentProvider.
             */
            val runResultUri = RunTracker2Kotlin.instance!!.contentResolver
                    .insert(Constants.URI_TABLE_RUN, cv)
            //Construct a String describing the results of the operation.
            val stringRunId: String?
            try {
                stringRunId = runResultUri?.lastPathSegment
                /*Assign the Run its ID if the operation was successful and notify the ContentResolver
                 *that the Run table has been changed.
                 */
                if (!(stringRunId != null && stringRunId == "")) {
                    val runId = java.lang.Long.valueOf(runResultUri!!.lastPathSegment)!!
                    try {
                        mRun.id = runId
                        RunTracker2Kotlin.instance!!.contentResolver
                                .notifyChange(Constants.URI_TABLE_RUN, null)
                        Log.i(TAG, "Called notifyChange on TABLE_RUN in InsertNewRunTask")
                    } catch (nfe: NumberFormatException) {
                        Log.d(TAG, "Last path segment of URI can't be parsed to a Long!!")
                    }

                }
            } catch (npe: NullPointerException) {
                Log.e(TAG, "Caught an NPE while extracting a path segment from a Uri")
            }


            /*Create an Intent with Extras to report the results of the operation. If the new Run
             *was created from the the RunRecyclerListFragment, the intent will return the Run to
             *the RunRecyclerListFragment, which will start the RunPagerActivity with the new Run's
             *RunId as an argument to set the current item for the ViewPager. If the new Run is
             *created from the RunPagerActivity, the intent will be returned to the RunPagerActivity
             *and the RunId will again be used to set the current item for the ViewPager. The
             *ViewPager will load the CombinedFragment for the new Run where the user can hit the
             *Start button to begin tracking the Run, which will start the loaders for the run and
             *set a Notification. The cursor loaders for the RunPagerActivity and the
             *RunRecyclerListFragment automatically update when the new Run is added to the Run
             *table in the database.
             */
            val localBroadcastManager = LocalBroadcastManager
                    .getInstance(RunTracker2Kotlin.instance!!)
            val responseIntent = Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_RUN)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, mRun)
            val receiver = localBroadcastManager.sendBroadcast(responseIntent)
            if (!receiver)
                Log.i(TAG, "No receiver for Insert Run responseIntent!")
        }
    }

    private class InsertLocationTask internal constructor(private val mRunId: Long, private val mLocation: Location) : Runnable {
        override fun run() {

            if (mRunId == -1L) {
                //Don't insert a Location unless there's valid RunId to go with it.
                Log.d(TAG, "RunId is -1 in attempt to insert location")
                return
            } else if (mRunId == 0L) {
                Log.d(TAG, "RunId is 0 in attempt to insert location.")
                return
            }
            val r = RunTracker2Kotlin.instance!!.resources
            var distance: Double
            var duration: Long
            val cv = ContentValues()
            var oldLocation: Location? = null
            //Construct a String to report the results of operation upon any failure.
            var resultString = ""
            val builder = StringBuilder(resultString)
            var run: Run? = null
            //Retrieve the Run specified in the method argument to make sure it's valid
            var cursor = RunTracker2Kotlin.instance!!.contentResolver
                    .query(Uri.withAppendedPath(Constants.URI_TABLE_RUN, mRunId.toString()),
                            null,
                            Constants.COLUMN_RUN_ID + " = ?",
                            arrayOf(mRunId.toString()),
                            null)
            if (cursor != null) {
                cursor.moveToFirst()
                if (!cursor.isAfterLast) {
                    run = RunDatabaseHelper.getRun(cursor)
                    if (run == null) {
                        //If there's no Run with the ID supplied, don't insert the Location.
                        return
                    }
                }
                cursor.close()
            } else {
                //A null cursor - no Run to associate this location with.
                return
            }
            /*Retrieve list of locations for the designated Run in order to get last previous
             *location to determine whether the Run can be continued at this point and time.
             */
            cursor = RunTracker2Kotlin.instance!!.contentResolver
                    .query(Uri.withAppendedPath(
                        Constants.URI_TABLE_LOCATION,
                        mRunId.toString()),
                        null,
                        Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                        arrayOf(mRunId.toString()),
                        Constants.COLUMN_LOCATION_TIMESTAMP + " desc"
            )
            if (cursor != null) {
                cursor.moveToFirst()
                if (!cursor.isAfterLast) {
                    oldLocation = RunDatabaseHelper.getLocation(cursor)
                }
                cursor.close()
            }
            if (oldLocation != null) {
                /*If the location is more than 100 meters distant from the last previous location
                 *and is more than 30 seconds more recent, the user is attempting to "continue" a
                 *Run from too distant a point. We need to check the time difference because
                 *sometimes in a moving vehicle the user can travel more than 100 meters before a
                 *location update gets processed, which would otherwise incorrectly terminate the
                 *Run.
                 */
                if (mLocation.distanceTo(oldLocation) > Constants.CONTINUATION_DISTANCE_LIMIT && mLocation.time - oldLocation.time > Constants.CONTINUATION_TIME_LIMIT) {
                    Log.i(TAG, "Aborting Run $mRunId for exceeding continuation distance limit.")
                    Log.i(TAG, "Old Location: " + oldLocation.toString() + " Current Location: " + mLocation.toString())
                    //Construct an error message and send it to the UI by broadcast intent.
                    builder.append(r.getString(R.string.current_location_too_distant))
                    resultString = builder.toString()
                    val responseIntent = Intent(Constants.SEND_RESULT_ACTION)
                            .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_LOCATION)
                            .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                            .putExtra(Constants.SHOULD_STOP, true)

                    //Broadcast the Intent so that the CombinedRunFragment UI can receive the result
                    val localBroadcastManager = LocalBroadcastManager
                            .getInstance(RunTracker2Kotlin.instance!!)
                    val receiver = localBroadcastManager.sendBroadcast(responseIntent)
                    if (!receiver) {
                        Log.i(TAG, "No receiver for Insert Location responseIntent!")
                    }
                    return
                }
            } else {
                Log.i(TAG, "oldLocation for Run $mRunId is null")
                /*If oldLocation is null, this is the first location entry for this run, so the
                 *"inappropriate continuation" situation is inapplicable.
                 */
            }


            /*Now that we know we have valid run, we can enter the new location in the Location
             *Table.
             */
            cv.put(Constants.COLUMN_LOCATION_LATITUDE, mLocation.latitude)
            cv.put(Constants.COLUMN_LOCATION_LONGITUDE, mLocation.longitude)
            cv.put(Constants.COLUMN_LOCATION_ALTITUDE, mLocation.altitude)
            cv.put(Constants.COLUMN_LOCATION_TIMESTAMP, mLocation.time)
            cv.put(Constants.COLUMN_LOCATION_PROVIDER, mLocation.provider)
            cv.put(Constants.COLUMN_LOCATION_RUN_ID, mRunId)

            val resultUri = RunTracker2Kotlin.instance!!.contentResolver
                    .insert(Constants.URI_TABLE_LOCATION, cv)
            val locationResult : String?
            try {
                locationResult = resultUri?.lastPathSegment
                if (locationResult != "") {
                    //A -1 return from the ContentResolver means the operation failed - report that.
                    if (Integer.parseInt(resultUri?.lastPathSegment) == -1) {
                        builder.append(r.getString(R.string.location_insert_failed, mRunId))
                    } else {
                        /*Upon successful insertion, notify the ContentResolver the Location table has
                     *changed.
                     */
                        RunTracker2Kotlin.instance!!.contentResolver
                                .notifyChange(Constants.URI_TABLE_LOCATION, null)
                        Log.i(TAG, "Called notifyChange on TABLE_LOCATION in insertNewLocationTask")
                        Log.i(TAG, "Inserted Location #" + Integer.parseInt(resultUri.lastPathSegment) +
                                " with timestamp " + mLocation.time)
                    }
                }
            } catch (npe: NullPointerException) {
                Log.e(TAG, "Caught an NPE while trying to extract a path segment from a Uri")
            }

            //With a valid new location, the Run's distance and duration can be updated.

            distance = run!!.distance
            duration = run.duration

            if (oldLocation != null) {
                /*This isn't the first location for this run, so calculate the increments of
                 *distance and time and add them to the cumulative total taken from the database.
                 */
                distance += mLocation.distanceTo(oldLocation).toDouble()
                val timeDifference = mLocation.time - oldLocation.time
                /*If it's been more than 30 seconds since the last location entry, the user must
                 *have hit the Stop button before and is now continuing the run. Rather than include
                 *all the time elapsed during the "interruption," keep the old Duration and add to
                 *that as the Run continues.
                 */
                if (timeDifference < Constants.CONTINUATION_TIME_LIMIT) {

                    duration += timeDifference
                }
            }
            /*If oldLocation is null, this is the first location entry for this run, so we
             *just keep the initial 0.0 and 0 values for the run's Distance and Duration. Now insert
             *the Run's distance and duration values into the Run table.
             */
            val runCv = ContentValues()
            runCv.put(Constants.COLUMN_RUN_DISTANCE, distance)
            runCv.put(Constants.COLUMN_RUN_DURATION, duration)
            val runResult = RunTracker2Kotlin.instance!!.contentResolver
                    .update(Uri.withAppendedPath(
                            Constants.URI_TABLE_RUN,
                            run.id.toString()),
                            runCv,
                            Constants.COLUMN_RUN_ID + " = ?",
                            arrayOf(mRunId.toString()))
            //Report a failure and upon success, notify ContentResolver that the Run table has changed.
            if (runResult == -1) {
                builder.append(r.getString(R.string.duration_and_distance_update_failure, mRunId))
            } else {
                RunTracker2Kotlin.instance!!.contentResolver
                        .notifyChange(Constants.URI_TABLE_RUN, null)
                Log.i(TAG, "Called notifyChange on TABLE_RUN in insertNewLocationTask")
            }
            resultString = builder.toString()
            if (resultString != "") {
                /*Create an Intent with Extras to report the results of the operation to the
                 *CombinedRunFragment UI and advise the user if there was an error. The
                 *CombinedRunFragment and RunRecyclerListFragment UIs get the new data fed to them
                 *automatically by loaders.
                 */
                val responseIntent = Intent(Constants.SEND_RESULT_ACTION)
                        .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_LOCATION)
                        .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                        .putExtra(Constants.SHOULD_STOP, false)
                //Broadcast the Intent so that the CombinedFragment UI can receive the result
                val localBroadcastManager = LocalBroadcastManager
                        .getInstance(RunTracker2Kotlin.instance!!)
                val receiver = localBroadcastManager.sendBroadcast(responseIntent)
                if (!receiver)
                    Log.i(TAG, "No receiver for Insert Location responseIntent!")
            }
        }
    }

    /*This Runnable task will update a Run's starting date when the first location update for this
     *Run is received to the Location's date and time. A StartAddress may be available also, so
     *check for that and update that field of the Run also.
     */
    private class UpdateStartDateTask internal constructor(internal val mRun: Run?) : Runnable {
        override fun run() {
            if (mRun == null) {
                return
            }
            //Load the new values in ContentValues and insert into this Run's record.
            val cv = ContentValues()
            cv.put(Constants.COLUMN_RUN_START_DATE, mRun.startDate!!.time)
            cv.put(Constants.COLUMN_RUN_START_ADDRESS, mRun.startAddress)
            val result = RunTracker2Kotlin.instance!!.contentResolver.update(
                    Uri.withAppendedPath(Constants.URI_TABLE_RUN, mRun.id.toString()),
                    cv,
                    Constants.COLUMN_RUN_ID + " = ?",
                    arrayOf(mRun.id.toString()))
            /*This operation should always update only one row of the Run table, so if result is
             *anything other than 1, report the result to the UI fragments.
             */
            if (result != 1) {
                /*Create an Intent with Extras to report the results of the operation to the
                 *CombinedFragment UI where the relevant loaders can be restarted.
                 *RunRecyclerListFragment relies on its cursor loader to get this data.
                 */
                val responseIntent = Intent(Constants.SEND_RESULT_ACTION)
                        .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_UPDATE_START_DATE)
                        .putExtra(Constants.ARG_RUN_ID, mRun.id)
                        .putExtra(Constants.EXTENDED_RESULTS_DATA, result)
                //Broadcast the Intent so that the UI can receive the result
                val localBroadcastManager = LocalBroadcastManager
                        .getInstance(RunTracker2Kotlin.instance!!)
                val receiver = localBroadcastManager.sendBroadcast(responseIntent)
                if (!receiver)
                    Log.i(TAG, "No receiver for Update Start Date responseIntent!")
            } else {
                //Upon success, notify the ContentResolver that the Run table has changed.
                RunTracker2Kotlin.instance!!.contentResolver
                        .notifyChange(Constants.URI_TABLE_RUN, null)
                Log.i(TAG, "Called norifyChange on TABLE-RUN in UpdateStartDateTask")
            }
        }
    }

    //If a bad or non-existent StartAddress got recorded the first around, this task will update it.
    private class UpdateStartAddressTask internal constructor(private val mRun: Run) : Runnable {
        override fun run() {

            //Get address from Geocoder for first location received for this Run.
            try {
                val location = RunManager.getStartLocationForRun(mRun.id)
                val latLng = LatLng(location!!.latitude, location.longitude)
                val startAddress = RunManager.getAddress(RunTracker2Kotlin.instance, latLng)
                //Update the current Run with the address we get
                mRun.startAddress = startAddress
                //Update the database with the new start address
                val cv = ContentValues()
                cv.put(Constants.COLUMN_RUN_START_ADDRESS, startAddress)
                val i = RunTracker2Kotlin.instance!!.contentResolver.update(
                        Uri.withAppendedPath(Constants.URI_TABLE_RUN, mRun.id.toString()),
                        cv,
                        Constants.COLUMN_RUN_ID + " = ?",
                        arrayOf(mRun.id.toString())
                )
                /*This operation should update only one row of the Run table, so i should be 1. If
                 *not, something went wrong, so report the error back to the UI fragments.
                 */
                if (i != 1) {
                    //Send the results of the update operation to the UI using a local broadcast
                    val localBroadcastManager = LocalBroadcastManager.getInstance(RunTracker2Kotlin.instance!!)
                    val resultIntent = Intent(Constants.SEND_RESULT_ACTION)
                            .putExtra(Constants.ACTION_ATTEMPTED,
                                    Constants.ACTION_UPDATE_START_ADDRESS)
                            .putExtra(Constants.EXTENDED_RESULTS_DATA, i)
                            .putExtra(Constants.UPDATED_ADDRESS_RESULT, startAddress)
                    val receiver = localBroadcastManager.sendBroadcast(resultIntent)
                    if (!receiver)
                        Log.i(TAG, "No receiver for StartAddressUpdate resultIntent!")
                } else {
                    //Upon success, notify ContentResolver that the Run table has changed.
                    RunTracker2Kotlin.instance!!.contentResolver
                            .notifyChange(Constants.URI_TABLE_RUN, null)
                    Log.i(TAG, "Called notifyChange on TABLE_RUN in UpdateStartAddressTask")
                }
            } catch (npe: NullPointerException) {
                Log.i(TAG, "No Start Location available - UpdateStartAddressTask skipped")
            } catch (re: RuntimeException) {
                Log.i(TAG, "In run(), Runtime Exception!!!")
                re.printStackTrace()
            } catch (e: Exception) {
                Log.i(TAG, " in run(), Exception!!!")
                e.cause
                e.printStackTrace()
            }

        }

    }

    /*This Runnable task updates the Run's End Address periodically and when location updates
     *stopped.
     */
    internal class UpdateEndAddressTask(private val mRun: Run?) : Runnable {
        override fun run() {
            Log.i(TAG, "In run() function for UpdateEndAddressTask in BackgroundLocationService for Run " + mRun?.id)

            try {
                //If we're not tracking any Runs, throw an Exception to stop running update EndAddress tasks.
                if (!isTrackingRun) {
                    Log.i(TAG, "Throwing exception to stop EndAddress updating")
                    throw RuntimeException("Exception to stop EndAddress updates")
                }
                if (mRun == null){
                    return
                }

                //Get address from Geocoder for latest location received for this Run.
                val location = RunManager.getLastLocationForRun(mRun.id)
                val latLng = LatLng(location!!.latitude, location.longitude)
                val endAddress = RunManager.getAddress(RunTracker2Kotlin.instance, latLng)
                //Update the current run object with the address we get
                mRun.endAddress = endAddress
                //Now save the new EndAddress to the Run's record the Run table.
                val cv = ContentValues()
                cv.put(Constants.COLUMN_RUN_END_ADDRESS, endAddress)
                val i = RunTracker2Kotlin.instance!!.contentResolver.update(
                        Uri.withAppendedPath(Constants.URI_TABLE_RUN, mRun.id.toString()),
                        cv,
                        Constants.COLUMN_RUN_ID + " = ?",
                        arrayOf(mRun.id.toString())
                )
                /*This operation should update only one row of the Run table, so i should be 1. If
                 *not, something went wrong, so report the error back to the UI fragments.
                 */
                if (i != 1) {
                    //Send the results of the update operation to the UI using a local broadcast
                    val localBroadcastManager = LocalBroadcastManager
                            .getInstance(RunTracker2Kotlin.instance!!)
                    val resultIntent = Intent(Constants.SEND_RESULT_ACTION)
                            .putExtra(Constants.ACTION_ATTEMPTED,
                                    Constants.ACTION_UPDATE_END_ADDRESS)
                            .putExtra(Constants.EXTENDED_RESULTS_DATA, i)
                            .putExtra(Constants.UPDATED_ADDRESS_RESULT, endAddress)
                    val receiver = localBroadcastManager.sendBroadcast(resultIntent)
                    if (!receiver)
                        Log.i(TAG, "No receiver for EndAddressUpdate resultIntent!")
                } else {
                    //Upon success, notify ContentResolver that the Run table has changed.
                    RunTracker2Kotlin.instance!!.contentResolver
                            .notifyChange(Constants.URI_TABLE_RUN, null)
                    Log.i(TAG, "Called notifyChange on TABLE_RUN in UpdateEndAddressTask")
                }

            } catch (npe: NullPointerException) {
                Log.i(TAG, "No Last Location available - UpdateEndAddressTask skipped")
            }

            /*Null Pointer is the only Exception we want to catch here - we have to have an uncaught
             *Exception to stop EndAddress updating, so the RunTimeException we throw has to go
             *uncaught to serve its intended purpose.
             */
        }
    }

    /*This Runnable task takes a list of Runs and deletes each of them.
     */
    private class DeleteRunsTask internal constructor(private val mRunIds: ArrayList<Long>) : Runnable {
        override fun run() {
            //Keep track of number of Runs deleted.
            var runsDeleted: Long = 0
            //Keep track of number of Locations deleted.
            var locationsDeleted: Long = 0
            val r = RunTracker2Kotlin.instance!!.resources
            //Create a String to report the results of the deletion operation.
            val stringBuilder = StringBuilder()
            /*We need to keep track of which Runs were successfully deleted and which were not so
             *results can be accurately reported. We must use a LinkedHashMap so that the results
             *will be recorded in the same order the Runs were processed.
             */
            val wasRunDeleted = LinkedHashMap<Long, Boolean>(mRunIds.size)
            //Iterate over all the items in the List selected for deletion.
            for (i in mRunIds.indices) {
                //First, delete all the locations associated with a Run to be deleted.
                val deletedLocations = RunTracker2Kotlin.instance!!.contentResolver.delete(
                        Constants.URI_TABLE_LOCATION,
                        Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                        arrayOf(mRunIds[i].toString())
                )
                /*Update total number of locations deleted and notify ContentResolver that the
                 *Location table has changed.
                 */
                if (deletedLocations >= 0) {
                    locationsDeleted += deletedLocations.toLong()
                    RunTracker2Kotlin.instance!!.contentResolver
                            .notifyChange(Constants.URI_TABLE_LOCATION, null)
                    Log.i(TAG, "Called notifyChange on TABLE_LOCATION in DeleteRunsTask")
                }
                //After deleting its Locations, delete the selected Run
                val deletedRun = RunTracker2Kotlin.instance!!.contentResolver.delete(
                        Uri.withAppendedPath(Constants.URI_TABLE_RUN, mRunIds[i].toString()),
                        Constants.COLUMN_RUN_ID + " = ?",
                        arrayOf(mRunIds[i].toString())
                )
                /*Update number of Runs deleted and notify the ContentResolver that the Run table
                 *changed.
                 */
                if (deletedRun >= 0) {
                    runsDeleted += deletedRun.toLong()
                    RunTracker2Kotlin.instance!!.contentResolver
                            .notifyChange(Constants.URI_TABLE_RUN, null)
                    Log.i(TAG, "Called notifyChange on TABLE_RUN in DeleteRunsTask")
                }
                when (deletedRun) {
                /*One Run deleted means success, so mark it as so in the LinkedHashMap and append
                 *the result to the report of the entire operation.
                 */
                    1 -> {
                        stringBuilder.append(r.getString(R.string.delete_run_success, mRunIds[i]))
                        wasRunDeleted.put(mRunIds[i], true)
                    }
                /*Zero Runs deleted means failure to delete, so mark it as such in the
                 *LinkedHashMap and append the result to the report of the entire operation.
                 */

                    0 -> {
                        stringBuilder.append(r.getString(R.string.delete_run_failure, mRunIds[i]))
                        wasRunDeleted.put(mRunIds[i], false)
                    }
                /*A -1 result means there was an error. Mark the deletion as a failure in the
                 *LinkedHashMap and add a description of the result to the report on results of
                 *the entire operation.
                 */

                    -1 -> {
                        stringBuilder.append(r.getString(R.string.delete_run_error, mRunIds[i]))
                        wasRunDeleted.put(mRunIds[i], false)
                    }

                    else -> {
                        stringBuilder.append(r.getString(R.string.delete_run_unexpected_return,
                                mRunIds[i]))
                        wasRunDeleted.put(mRunIds[i], false)
                    }
                }
                /*if (deletedRun == 1) {
                    *//*One Run deleted means success, so mark it as so in the LinkedHashMap and append
                     *the result to the report of the entire operation.
                     *//*
                    stringBuilder.append(r.getString(R.string.delete_run_success, mRunIds[i]))
                    wasRunDeleted.put(mRunIds[i], true)
                } else if (deletedRun == 0) {
                    *//*Zero Runs deleted means failure to delete, so mark it as such in the
                     *LinkedHashMap and append the result to the report of the entire operation.
                     *//*
                    stringBuilder.append(r.getString(R.string.delete_run_failure, mRunIds[i]))
                    wasRunDeleted.put(mRunIds[i], false)
                } else if (deletedRun == -1) {
                    *//*A -1 result means there was an error. Mark the deletion as a failure in the
                     *LinkedHashMap and add a description of the result to the report on results of
                     *the entire operation.
                     *//*
                    stringBuilder.append(r.getString(R.string.delete_run_error, mRunIds[i]))
                    wasRunDeleted.put(mRunIds[i], false)
                } else {
                    stringBuilder.append(r.getString(R.string.delete_run_unexpected_return,
                            mRunIds[i]))
                    wasRunDeleted.put(mRunIds[i], false)
                }*/
                //Append report on deletion of locations for this Run to the report on the Run.
                if (deletedLocations == -1) {
                    stringBuilder.append(r.getString(
                            R.string.delete_locations_error, mRunIds[i]))
                } else {
                    stringBuilder.append(r.getQuantityString(R.plurals.location_deletion_results,
                            deletedLocations,
                            deletedLocations,
                            mRunIds[i]))
                }
            }
            //Insert a total summary report sentence at the beginning of the entire report of results.
            stringBuilder.insert(0, r.getQuantityString(R.plurals.runs_deletion_results,
                    runsDeleted.toInt(), runsDeleted.toInt(), locationsDeleted))
            //Now construct the report String and put in into a broadcast intent for the UI element.
            val resultString = stringBuilder.toString()
            /*Create an Intent with Extras to report the results of the operation. This Intent is
             *aimed at a different Activity/Fragment than the other broadcasts, the
             *RunRecyclerListFragment, so it has a different Action specified. All the others are
             *directed at the CombinedFragment or the RunManager. The RunRecyclerListFragment needs
             *to get this broadcast so it can call a floating dialog to display the results of the
             *delete operation. Its RecyclerView will update automatically by operation of its
             *cursor loader.
             */
            val responseIntent = Intent(Constants.ACTION_DELETE_RUNS)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                    .putExtra(Constants.EXTRA_VIEW_HASHMAP, wasRunDeleted)
            //Broadcast the Intent so that the UI can receive the result
            val localBroadcastManager = LocalBroadcastManager.getInstance(RunTracker2Kotlin.instance!!)
            val receiver = localBroadcastManager.sendBroadcast(responseIntent)
            if (!receiver)
                Log.i(TAG, "No receiver for Delete Runs responseIntent!")
        }
    }

    /*This Runnable task deletes a single Run. It is used from the RunPagerActivity where only a
     *single Run at a time can be selected for deletion - the Run currently displayed by the
     *ViewPager.
     */
    private class DeleteRunTask internal constructor(private val mRunId: Long) : Runnable {

        override fun run() {
            //First delete the locations for the selected Run.
            val locationsDeleted = RunTracker2Kotlin.instance!!.contentResolver.delete(
                    Constants.URI_TABLE_LOCATION,
                    Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                    arrayOf(mRunId.toString())
            )
            if (locationsDeleted > 0) {
                RunTracker2Kotlin.instance!!.contentResolver.notifyChange(Constants.URI_TABLE_LOCATION, null)
                Log.d(TAG, "Notified ContentResolver of change in location table for deletion of Run " + mRunId)
            }
            //Now delete the selected Run.
            val runsDeleted = RunTracker2Kotlin.instance!!.contentResolver.delete(
                    Uri.withAppendedPath(Constants.URI_TABLE_RUN, mRunId.toString()),
                    Constants.COLUMN_RUN_ID + " = ?",
                    arrayOf(mRunId.toString())
            )
            Log.d(TAG, runsDeleted.toString() + " Run deleted for Run " + mRunId)
            Log.d(TAG, locationsDeleted.toString() + " locations deleted for Run " + mRunId)
            if (runsDeleted > 0) {
                RunTracker2Kotlin.instance!!.contentResolver
                        .notifyChange(Constants.URI_TABLE_RUN, null)
                Log.d(TAG, "Notified ContentResolver of change in Run table for deletion of Run " + mRunId)
            }
            val responseIntent = Intent(Constants.ACTION_DELETE_RUN)
                    /*Send results of the deletion operation to the RunPagerActivity, which can then
                 *assemble a dialog advising the user of the results.
                 */
                    .putExtra(Constants.PARAM_RUN, mRunId)
                    .putExtra(Constants.LOCATIONS_DELETED, locationsDeleted)
                    /*Put the runId here so the RunPagerActivity will know which Run has been
                 *deleted.
                 */
                    .putExtra(Constants.RUNS_DELETED, runsDeleted)
            val localBroadcastManager = LocalBroadcastManager.getInstance(RunTracker2Kotlin.instance!!)
            val receiver = localBroadcastManager.sendBroadcast(responseIntent)
            if (!receiver)
                Log.i(TAG, "No receiver for Delete Run responseIntent!")
        }
    }

    companion object {

        private val TAG = "RunManager"
        @SuppressLint("StaticFieldLeak")
        private var sAppContext: Context? = RunTracker2Kotlin.instance
        @SuppressLint("StaticFieldLeak")
        private var sRunManager: RunManager? = null
        //LongSparseArray to associate location objects with Bounds for use in displaying GoogleMap
        private val sBoundsMap = LongSparseArray<WeakReference<LatLngBounds>>()
        //LongSparseArray to associate location objects with Points used to make Polyline on GoogleMap
        private val sPointsMap = LongSparseArray<WeakReference<List<LatLng>>>()
        private val sLocationCountMap = LongSparseArray<Int>()
        //Executor for running database operations on background threads
        private val sExecutor = ScheduledThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors() + 1)
        /*Handle for the recurring task of updating Ending Addresses; needed so task can be cancelled
     *when we're not tracking runs
     */
        private var sScheduledFuture: ScheduledFuture<*>? = null
        internal var currentRunId: Long = 0
            private set

        operator fun get(c: Context): RunManager {
            if (sRunManager == null) {
                sRunManager = RunManager(c)
            }
            return sRunManager!!
        }

        /*When starting location updates, call with shouldCreate true, so the PendingIntent will be
     *returned; when calling just to check if any Run is being tracked, call with shouldCreate
     *false; if we've created the PendingIntent to start location updates, this will return the
     *existing PendingIntent, but if not, this will not create the PendingIntent, but rather return
     *null, thus indicating that no Run is being tracked because there are no location updates.
     */
        internal fun getLocationPendingIntent(context: Context, shouldCreate: Boolean): PendingIntent? {
            val broadcast = Intent(Constants.ACTION_LOCATION)
            broadcast.setClass(RunTracker2Kotlin.instance!!, TrackingLocationReceiver::class.java)
            val flags = if (shouldCreate) 0 else PendingIntent.FLAG_NO_CREATE
            return PendingIntent.getBroadcast(context, 0, broadcast, flags)
        }

        //Get a Run from the database using its RunId
        internal fun getRun(id: Long): Run? {
            var run: Run? = null
            val cursor = sAppContext!!.contentResolver.query(
                    Uri.withAppendedPath(Constants.URI_TABLE_RUN, id.toString()), null,
                    Constants.COLUMN_RUN_ID + " = ?",
                    arrayOf(id.toString()), null)
            if (cursor != null) {
                cursor.moveToFirst()
                //If you got a row, get a run
                if (!cursor.isAfterLast)
                    run = RunDatabaseHelper.getRun(cursor)
                cursor.close()
            }
            return run
        }

        //Return the starting (i.e., first) location object recorded for the given Run
        internal fun getStartLocationForRun(runId: Long): Location? {
            var location: Location? = null
            val cursor = sAppContext!!.contentResolver.query(
                    Uri.withAppendedPath(Constants.URI_TABLE_LOCATION, runId.toString()), null,
                    Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                    arrayOf(runId.toString()),
                    Constants.COLUMN_LOCATION_TIMESTAMP + " asc"
            )
            if (cursor != null) {
                cursor.moveToFirst()
                //If you got a row, get a location
                if (!cursor.isAfterLast)
                    location = RunDatabaseHelper.getLocation(cursor)
                cursor.close()
            }
            return location
        }

        //Return the latest location object recorded for the given Run.
        internal fun getLastLocationForRun(runId: Long): Location? {
            var location: Location? = null
            val cursor = sAppContext!!.contentResolver.query(
                    Uri.withAppendedPath(Constants.URI_TABLE_LOCATION, runId.toString()), null,
                    Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                    arrayOf(runId.toString()),
                    Constants.COLUMN_LOCATION_TIMESTAMP + " desc"
            )
            if (cursor != null) {
                cursor.moveToFirst()
                //If you got a row, get a location
                if (!cursor.isAfterLast)
                    location = RunDatabaseHelper.getLocation(cursor)
                cursor.close()
            }
            return location
        }

        //Save the SparseArray associating a given Run with the Bounds needed to display it.
        internal fun saveBounds(runId: Long?, bounds: LatLngBounds) {
            sBoundsMap.put(runId!!, WeakReference(bounds))
        }

        //Get the the Bounds for a particular Run
        internal fun retrieveBounds(runId: Long?): LatLngBounds? {
            val latLngBoundsWeakReference = sBoundsMap.get(runId!!)
            return latLngBoundsWeakReference?.get()
        }

        //Save the SparseArray associating a given Run with its LocationCount
        internal fun saveLocationCount(runId: Long?, locationCount: Int) = sLocationCountMap.put(runId!!, locationCount)

        //Retrieve the LocationCount for particular Run
        internal fun getLocationCount(runId: Long?): Int = sLocationCountMap.get(runId!!, 0)

        //Save the SparseArray associating a Run with the locations, expressed as LatLngs, for that Run
        internal fun savePoints(runId: Long?, points: List<LatLng>) {
            sPointsMap.put(runId!!, WeakReference(points))
        }

        //Retrieve the list of locations, expressed as LatLngs, associated with a given Run
        internal fun retrievePoints(runId: Long?): List<LatLng>? {
            val listWeakReference = sPointsMap.get(runId!!)
            return listWeakReference?.get()
        }


        /*Function to return the street address of the nearest building to the LatLng object
     *passed in as an argument - used in the CombinedFragment UI
     */
        internal fun getAddress(context: Context?, loc: LatLng?): String {
            Log.i(TAG, "Reached getAddress(Context, LatLng)")
            //Get a geocoder from Google Play Services and use its output to build an address string or
            //an error message, depending upon result.
            var filterAddress = ""
            val geocoder = Geocoder(context)
            //Log.i(TAG, "Geocoder is: " + geocoder);
            when {
                (loc == null) -> {
                    Log.i(TAG, "Location is null in geocoding getString()")
                    filterAddress = context!!.getString(R.string.lastlocation_null)
                }
                (Geocoder.isPresent()) -> {
                    //need to check whether the getFromLocation() method is available
                    //Log.i(TAG, "Geocoder is present");
                    try {
                        //The geocoder will return a list of addresses. We want only one, hence the final
                        //argument to this method.
                        val addresses : List<Address>? = geocoder.getFromLocation(
                                loc.latitude, loc.longitude, 1)
                        //Any address result will be a List element, even though we're getting only a single
                        //result
                        if (addresses != null && addresses.isNotEmpty()) {
                            val address = addresses[0]
                            val addressFragments = (0..address.maxAddressLineIndex).map { address.getAddressLine(it) }
                            /*Convert address to a single string with line separators to divide elements of
                         *the address
                         */
                            filterAddress = TextUtils.join(System.getProperty("line separator"),
                                    addressFragments)
                        } else {
                            Log.i(TAG, "Address is empty")
                        }


                    } catch (ioe: IOException) {
                        Log.i(TAG, "IO error in geocoder.")
                        filterAddress = context!!.getString(R.string.geocoder_io_error)
                        ioe.printStackTrace()
                    } catch (iae: IllegalArgumentException) {
                        Log.i(TAG, "Bad latitude or longitude argument")
                        filterAddress = context!!.getString(R.string.geocoder_bad_argument_error)
                    }

                }
                else -> {
                    Log.i(TAG, "getFromLocation() functionality missing.")
                    filterAddress = context!!.getString(R.string.get_address_function_unavailable)
                }
            }
            return filterAddress
        }

        /*//Check to see if we got a useful value when trying to look up an address. If the address is
     *"bad" we can check again for the address for a given LatLng
     */
        internal fun addressBad(context: Context, address: String): Boolean {
            val r = context.resources

            return address.compareTo("", ignoreCase = true) == 0 ||
                    address.compareTo(r.getString(R.string.geocoder_io_error), ignoreCase = true) == 0 ||
                    address.compareTo(r.getString(R.string.geocoder_bad_argument_error), ignoreCase = true) == 0 ||
                    address.compareTo(r.getString(R.string.lastlocation_null), ignoreCase = true) == 0 ||
                    address.compareTo(r.getString(R.string.get_address_function_unavailable), ignoreCase = true) == 0
        }

        /*Are we tracking ANY Run? Note that getLocationPendingIntent(boolean) in this class is used by
     *the BackgroundLocationService to get the PendingIntent used to start and stop location updates.
     *If the call to getLocationPendingIntent returns null, we know that location updates have not
     *been started and no Run is being tracked.
     */
        internal val isTrackingRun: Boolean
            get() = getLocationPendingIntent(sAppContext!!, false) != null

        //Are we tracking the specified Run?
        internal fun isTrackingRun(run: Run?): Boolean = isTrackingRun && run != null && run.id == currentRunId

        //Format output of distance values depending upon whether we're using Metric or Imperial measures.
        internal fun formatDistance(meters: Double): String {
            val system = RunTracker2Kotlin.prefs!!.getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)
            return if (system == Constants.METRIC) {
                if (meters < 1000f) {
                    String.format(Locale.US, "%.0f", meters) + " meters"
                } else {
                    String.format(Locale.US, "%.2f", meters / 1000) + " kilometers"
                }
            } else {
                val feet = meters * Constants.METERS_TO_FEET
                if (feet < 5280.0f) {
                    String.format(Locale.US, "%.0f", feet) + " feet"
                } else {
                    String.format(Locale.US, "%.2f", meters * Constants.METERS_TO_MILES) + " miles"
                }
            }
        }

        //Format output of altitude values depending upon whether we're using Metric or Imperial measures.
        internal fun formatAltitude(meters: Double): String {
            val system = RunTracker2Kotlin.prefs!!
                    .getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)
            return if (system == Constants.METRIC) {
                String.format(Locale.US, "%.0f", meters) + " meters"
            } else {
                String.format(Locale.US, "%.0f",
                        meters * Constants.METERS_TO_FEET) + " feet"
            }
        }

        /*This method converts the +- decimal degrees formatted locations produced by Location Services
     *to N/S/E/W degrees/minutes/seconds format with the conventional symbols for degrees, minutes
     *and seconds.
     */
        internal fun convertLocation(latitude: Double, longitude: Double): String {
            val builder = StringBuilder()
            //Unicode for the little circle degree symbol
            val degree: Char = 0x00B0.toChar()
            /*First format the latitude value. Android assigns positive values to northern hemisphere
         *latitudes and negative values to the southern hemisphere, so assign letters accordingly.
         */
            if (latitude < 0) {
                builder.append("S ")
            } else {
                builder.append("N ")
            }
            /*First strip the negative/positive designation and then get degrees/minutes/seconds colon-
         *separated format Android makes available in its Location API..
         */
            val latitudeDegrees = Location.convert(Math.abs(latitude), Location.FORMAT_SECONDS)
            //Separate the degrees, minutes and seconds values and add the appropriate symbols.
            val latitudeSplit = latitudeDegrees.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            builder.append(latitudeSplit[0])
            builder.append(degree)
            builder.append(latitudeSplit[1])
            builder.append("'")
            builder.append(latitudeSplit[2])
            builder.append("\"")
            //Separate the latitude and longitude values
            builder.append(" ")
            /*Now convert the longitude value. Android assigns positive values to locations east of 0
         *degrees longitude and negative values to locations west of there, so assign symbols accordingly.
         */
            if (longitude < 0) {
                builder.append("W ")
            } else {
                builder.append("E ")
            }
            //Repeat same process as used for the latitude figure.
            val longitudeDegrees = Location.convert(Math.abs(longitude), Location.FORMAT_SECONDS)
            val longitudeSplit = longitudeDegrees.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            builder.append(longitudeSplit[0])
            builder.append(degree)
            builder.append(longitudeSplit[1])
            builder.append("'")
            builder.append(longitudeSplit[2])
            builder.append("\"")

            return builder.toString()
        }

        /*Series of static methods to invoke Runnable tasks to be executed by
     *the  Executor service on different threads.
     */
        internal fun insertRun() {
            sExecutor.execute(InsertNewRunTask(Run()))
        }

        internal fun updateStartDate(run: Run) {
            sExecutor.execute(UpdateStartDateTask(run))
        }

        internal fun updateStartAddress(run: Run) {
            sExecutor.execute(UpdateStartAddressTask(run))
        }

        internal fun updateEndAddress(run: Run) {
            sExecutor.execute(UpdateEndAddressTask(run))
        }

        internal fun deleteRuns(runIds: ArrayList<Long>) {
            sExecutor.execute(DeleteRunsTask(runIds))
        }

        internal fun deleteRun(runId: Long?) {
            sExecutor.execute(DeleteRunTask(runId!!))
        }
    }
}

