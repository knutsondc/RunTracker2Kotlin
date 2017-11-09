package com.dknutsonlaw.android.runtracker2kotlin

/*
  Created by dck on 9/6/15. An {@link IntentService} subclass for handling database task requests asynchronously in
  a service on a separate handler thread.
 */
import android.app.IntentService
import android.content.ContentValues
import android.content.Intent
import android.content.Context
import android.location.Location
import android.net.Uri
import android.os.Build
import android.support.v4.content.LocalBroadcastManager
import android.util.Log

import com.google.android.gms.maps.model.LatLng

import java.util.ArrayList
import java.util.LinkedHashMap

/** Created by dck on 2/11/2015
 * An [IntentService] subclass for handling database task requests asynchronously in
 * a service on a separate handler thread.
 *
 * 2/18/2015 - Finally implemented startActionInsertRun(), handleActionInsertRun() and related
 * changes to onHandleIntent().
 *
 * 8/14/2015 - Added reverse geocoding address function.
 *
 * 11/11/15 - Added checkEndAddress method
 */
class TrackingLocationIntentService : IntentService("TrackingLocationIntentService") {

    //We use local broadcasts to transmit results of the IntentService's actions back
    //to the UI fragments.
    private val mLocalBroadcastManager = LocalBroadcastManager.getInstance(this)


    //onHandleIntent is always the initial entry point in an IntentService.
    override fun onHandleIntent(intent: Intent?) {
        startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.notification)
        //startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.createNotification(this));
        //Dispatch Intents to different methods for processing, depending upon their Actions,
        if (intent != null) {
            val action = intent.action
            when (action) {
                Constants.ACTION_INSERT_RUN -> {
                    val run = intent.getParcelableExtra<Run>(Constants.PARAM_RUN)
                    handleActionInsertRun(run)
                }
                Constants.ACTION_DELETE_RUN -> {
                    val runId = intent.getLongExtra(Constants.PARAM_RUN_IDS, -1)
                    handleActionDeleteRun(runId)
                }
                Constants.ACTION_DELETE_RUNS -> {
                    val runIds = intent.getSerializableExtra(Constants.PARAM_RUN_IDS) as ArrayList<Long>
                    //ArrayList<Integer> viewsToDelete = intent.getIntegerArrayListExtra(Constants.VIEWS_TO_DELETE);
                    handleActionDeleteRuns(runIds/*, viewsToDelete*/)
                }
                Constants.ACTION_INSERT_LOCATION -> {
                    val runId = intent.getLongExtra(Constants.PARAM_RUN_IDS, -1)
                    val loc = intent.getParcelableExtra<Location>(Constants.PARAM_LOCATION)
                    handleActionInsertLocation(runId, loc)
                }
                Constants.ACTION_UPDATE_START_DATE -> {
                    val run = intent.getParcelableExtra<Run>(Constants.PARAM_RUN)
                    handleActionUpdateStartDate(run)
                }
                Constants.ACTION_UPDATE_START_ADDRESS -> {
                    val run = intent.getParcelableExtra<Run>(Constants.PARAM_RUN)
                    val location = intent.getParcelableExtra<Location>(Constants.PARAM_LOCATION)
                    handleActionUpdateStartAddress(run, location)
                }
                Constants.ACTION_UPDATE_END_ADDRESS -> {
                    val run = intent.getParcelableExtra<Run>(Constants.PARAM_RUN)
                    val location = intent.getParcelableExtra<Location>(Constants.PARAM_LOCATION)
                    handleActionUpdateEndAddress(run, location)
                }
                else -> Log.d(TAG, "How'd you get here!?! Unknown Action type!")
            }
        }
    }

    /**
     * Call the DatabaseHelper's method to insert a new Run into the Run table on
     * the provided background thread.
     */
    private fun handleActionInsertRun(run: Run) {
        Log.i(TAG, "Reached handleActionInsertRun")
        val cv = ContentValues()
        cv.put(Constants.COLUMN_RUN_START_DATE, run.startDate!!.time)
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.startAddress)
        cv.put(Constants.COLUMN_RUN_END_ADDRESS, run.endAddress)
        cv.put(Constants.COLUMN_RUN_DISTANCE, run.distance)
        cv.put(Constants.COLUMN_RUN_DURATION, run.duration)
        val runResultUri = contentResolver.insert(Constants.URI_TABLE_RUN, cv)
        var stringRunId: String? = ""
        try {
            stringRunId = runResultUri?.lastPathSegment
        } catch (npe: NullPointerException) {
            Log.e(TAG, "Caught an NPE while extracting a path segment from a Uri")
        }

        if (stringRunId != "") {
            val runId = java.lang.Long.valueOf(runResultUri?.lastPathSegment)!!
            run.id = runId
        }

        //Create an Intent with Extras to report the results of the operation. If the new Run was
        //created from the the RunRecyclerListFragment, the intent will return the Run to the
        //RunRecyclerListFragment, which will start the RunPagerActivity with the new Run's
        //RunId as an argument to set the current item for the ViewPager. If the new Run is created
        //from the RunPagerActivity, the intent will be returned to the
        //RunPagerActivity and the RunId will again be used to set the current item for the
        //ViewPager. The ViewPager will load the CombinedFragment for the new Run where the user can hit
        //the Start button to begin tracking the Run, which will start the loaders for the run and
        //set a Notification. The cursor loaders for the RunPagerActivity and the
        //RunRecyclerListFragment automatically update when the new Run is added to the Run table in
        //the database.
        val responseIntent = Intent(Constants.SEND_RESULT_ACTION)
                .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_RUN)
                .putExtra(Constants.EXTENDED_RESULTS_DATA, run)
        val receiver = mLocalBroadcastManager.sendBroadcast(responseIntent)
        if (!receiver)
            Log.i(TAG, "No receiver for Insert Run responseIntent!")
        stopForeground(true)
    }

    /*
     * Handle action InsertLocation in the provided background thread with the provided runId
     * and location parameters
     */
    private fun handleActionInsertLocation(runId: Long, location: Location) {
        val viewRunId = RunTracker2Kotlin.prefs!!.getLong(Constants.CURRENTLY_VIEWED_RUN, -1)
        if (runId != viewRunId || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.createNotification(this))
        }
        if (runId == -1L) {
            Log.d(TAG, "RunId is -1 in attempt to insert location")
            stopForeground(true)
            return
        }
        val r = resources
        var distance: Double
        var duration: Long
        val cv = ContentValues()
        var oldLocation: Location? = null
        var resultString = ""
        val builder = StringBuilder(resultString)
        var run: Run? = null
        //Retrieve the Run specified in the method argument to make sure it's valid
        var cursor = contentResolver.query(Uri.withAppendedPath(Constants.URI_TABLE_RUN, runId.toString()), null,
                Constants.COLUMN_RUN_ID + " = ?",
                arrayOf(runId.toString()), null)
        if (cursor != null) {
            Log.i(TAG, "Run cursor is not null")
            cursor.moveToFirst()
            if (!cursor.isAfterLast) {
                run = RunDatabaseHelper.getRun(cursor)
                Log.i(TAG, "Is run null? " + (run == null))
                if (run == null) {
                    stopForeground(true)
                    return
                }
            }
            cursor.close()
        } else {
            Log.i(TAG, "Run cursor was null")
            stopForeground(true)
            return
        }
        //Retrieve list of locations for the designated Run in order to get last previous location
        //to determine whether the Run can be continued at this point and time
        cursor = contentResolver.query(Uri.withAppendedPath(Constants.URI_TABLE_LOCATION, runId.toString()), null,
                Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                arrayOf(runId.toString()),
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
            //If the location is more than 100 meters distant from the last previous location and is
            //more than 30 seconds more recent, the user is attempting to "continue" a run from too
            //distant a point. We need to check the time difference because sometimes in a moving
            //vehicle the user can travel more than 100 meters before a location update gets
            //processed, which would otherwise incorrectly terminate the run.
            if (location.distanceTo(oldLocation) > Constants.CONTINUATION_DISTANCE_LIMIT && location.time - oldLocation.time > Constants.CONTINUATION_TIME_LIMIT) {
                Log.i(TAG, "Aborting Run $runId for exceeding continuation distance limit.")
                builder.append(r.getString(R.string.current_location_too_distant))
                resultString = builder.toString()
                val responseIntent = Intent(Constants.SEND_RESULT_ACTION)
                        .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_LOCATION)
                        .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                        .putExtra(Constants.SHOULD_STOP, true)

                //Broadcast the Intent so that the CombinedRunFragment UI can receive the result
                val receiver = mLocalBroadcastManager.sendBroadcast(responseIntent)
                if (!receiver) {
                    Log.i(TAG, "No receiver for Insert Location responseIntent!")
                }
                stopForeground(true)
                return
            }
        } else {
            Log.i(TAG, "oldLocation for Run $runId is null")
            //If oldLocation is null, this is the first location entry for this run, so the
            //"inappropriate continuation" situation is inapplicable.
        }

        if (run != null) {
            //Now that we know we have valid run, we can enter the new location in the Location Table.
            Log.i(TAG, "Now inserting location data in the ContentValues")
            cv.put(Constants.COLUMN_LOCATION_LATITUDE, location.latitude)
            cv.put(Constants.COLUMN_LOCATION_LONGITUDE, location.longitude)
            cv.put(Constants.COLUMN_LOCATION_ALTITUDE, location.altitude)
            cv.put(Constants.COLUMN_LOCATION_TIMESTAMP, location.time)
            cv.put(Constants.COLUMN_LOCATION_PROVIDER, location.provider)
            cv.put(Constants.COLUMN_LOCATION_RUN_ID, runId)
        } else {
            Log.d(TAG, "run in IntentService insertLocation is null!")
            stopForeground(true)
            return
        }
        Log.d(TAG, "URI_TABLE_LOCATION is: " + Constants.URI_TABLE_LOCATION.toString())
        val resultUri = contentResolver.insert(Constants.URI_TABLE_LOCATION, cv)
        var locationResult: String? = ""
        try {
            locationResult = resultUri?.lastPathSegment
        } catch (npe: NullPointerException) {
            Log.e(TAG, "Caught an NPE while trying to extract a path segment from a Uri")
        }

        if (locationResult != "") {
            if (Integer.parseInt(resultUri?.lastPathSegment) == -1) {
                builder.append(r.getString(R.string.location_insert_failed, runId))
            } else {
                contentResolver.notifyChange(Constants.URI_TABLE_LOCATION, null)
            }
        }

        distance = run.distance
        duration = run.duration

        if (oldLocation != null) {
            //This isn't the first location for this run, so calculate the increments of distance
            //and time and add them to the cumulative total taken from the database
            distance += location.distanceTo(oldLocation).toDouble()
            val timeDifference = location.time - oldLocation.time
            //If it's been more than 30 seconds since the last location entry, the user must
            //have hit the Stop button before and is now continuing the run. Rather than include
            //all the time elapsed during the "interruption," keep the old Duration and add to
            //that as the Run continues..
            if (timeDifference < Constants.CONTINUATION_TIME_LIMIT) {

                duration += timeDifference
            }
        } else {
            //If oldLocation is null, this is the first location entry for this run, so we
            //just keep the initial 0.0 and 0 values for the run's Distance and Duration
            Log.i(TAG, "oldLocation for Run $runId is null")
        }
        val runCv = ContentValues()
        runCv.put(Constants.COLUMN_RUN_DISTANCE, distance)
        runCv.put(Constants.COLUMN_RUN_DURATION, duration)
        Log.d(TAG, "URI for updating Run in IntentService insertLocation is " + Uri.withAppendedPath(Constants.URI_TABLE_RUN, runId.toString()).toString())

        val runResult = contentResolver.update(Uri.withAppendedPath(Constants.URI_TABLE_RUN, run.id.toString()),
                runCv,
                Constants.COLUMN_RUN_ID + " = ?",
                arrayOf(runId.toString()))

        if (runResult == -1) {
            builder.append(r.getString(R.string.duration_and_distance_update_failure, runId))
        } else {
            contentResolver.notifyChange(Constants.URI_TABLE_RUN, null)
        }
        resultString = builder.toString()
        if (resultString != "") {
            //Create an Intent with Extras to report the results of the operation to the CombinedRunFragment
            //UI and advise the user if there was an error. The CombinedRunFragment, RunRecyclerListFragment
            //and RunMapFragment UIs get the new data fed to them automatically by loaders.
            val responseIntent = Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_INSERT_LOCATION)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                    .putExtra(Constants.SHOULD_STOP, false)
            //Broadcast the Intent so that the CombinedRunFragment UI can receive the result
            val receiver = mLocalBroadcastManager.sendBroadcast(responseIntent)
            if (!receiver)
                Log.i(TAG, "No receiver for Insert Location responseIntent!")
        }
        stopForeground(true)
    }

    /*
     * Handle action UpdateStartDate in the provided background thread for Run provided in the
     * parameter
     */
    private fun handleActionUpdateStartDate(run: Run?) {
        //Perform the update on the database and get the result
        //int result = RunManager.getHelper().updateRunStartDate(mRunManager.mAppContext, run);
        //int result = RunManager.getHelper().updateRunStartDate(this, run);
        if (run == null) {
            stopForeground(true)
            return
        }
        val viewRunId = RunTracker2Kotlin.prefs!!.getLong(Constants.CURRENTLY_VIEWED_RUN, -1)
        /*if (run.getId() != viewRunId || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.createNotification(this));
        }*/
        val cv = ContentValues()
        cv.put(Constants.COLUMN_RUN_START_DATE, run.startDate!!.time)
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.startAddress)
        val result = contentResolver.update(Uri.withAppendedPath(
                Constants.URI_TABLE_RUN, run.id.toString()),
                cv,
                Constants.COLUMN_RUN_ID + " = ?",
                arrayOf(run.id.toString()))
        //This operation should always update only one row of the Run table, so if result is anything
        //other than 1, report the result to the UI fragments.
        if (result != 1) {
            //Create an Intent with Extras to report the results of the operation to the CombinedRunFragment
            //UI where the relevant loaders can be restarted. RunRecyclerListFragment relies on its cursor
            //loader to get this data.
            val responseIntent = Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_UPDATE_START_DATE)
                    .putExtra(Constants.ARG_RUN_ID, run.id)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, result)
            //Broadcast the Intent so that the UI can receive the result
            val receiver = mLocalBroadcastManager.sendBroadcast(responseIntent)
            if (!receiver)
                Log.i(TAG, "No receiver for Update Start Date responseIntent!")
        }
        stopForeground(true)
    }

    /*
     * Handle action UpdateStartAddress in the background thread for the Run parameter using the
     * location parameter
     */
    private fun handleActionUpdateStartAddress(run: Run?, location: Location?) {
        if (run == null || location == null) {
            Log.i(TAG, "Null value parameter passed into handleActionUpdateStartAddress()")
            stopForeground(true)
            return
        }
        /*long viewRunId = RunTracker2Kotlin.getPrefs().getLong(Constants.CURRENTLY_VIEWED_RUN, -1);
        if (run.getId() != viewRunId || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.createNotification(this));
        }*/
        val latLng = LatLng(location.latitude, location.longitude)
        val startAddress = RunManager.getAddress(this, latLng)
        run.startAddress = startAddress
        val cv = ContentValues()
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, startAddress)
        //Perform the update on the database and get the result
        val result = contentResolver.update(
                Uri.withAppendedPath(Constants.URI_TABLE_RUN, run.id.toString()),
                cv,
                Constants.COLUMN_RUN_ID + " = ?",
                arrayOf(run.id.toString())
        )
        //This operation should only affect one row of the Run table, so report any result other
        //than 1 back to the UI fragments.
        if (result != 1) {
            //Create an Intent with Extras to report the results of the operation to the CombinedRunFragment
            //UI where the relevant loaders can be restarted. RunRecyclerListFragment relies on its cursor
            //loader to get this data.
            val responseIntent = Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_UPDATE_START_ADDRESS)
                    .putExtra(Constants.ARG_RUN_ID, run.id)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, result)
                    .putExtra(Constants.UPDATED_ADDRESS_RESULT, startAddress)
            //Broadcast the Intent so that the UI can receive the result
            val receiver = mLocalBroadcastManager.sendBroadcast(responseIntent)
            if (!receiver)
                Log.i(TAG, "No receiver for Update Start Date responseIntent!")
        }
        stopForeground(true)
    }

    /*
     * Handle action UpdateEndAddress in the background thread for the Run parameter using the
     * location parameter
     */
    private fun handleActionUpdateEndAddress(run: Run?, location: Location?) {
        if (run == null || location == null) {
            Log.i(TAG, "Null value parameter passed into handleActionUpdateEndAddress()")
            stopForeground(true)
            return
        }
        /*long viewRunId = RunTracker2Kotlin.getPrefs().getLong(Constants.CURRENTLY_VIEWED_RUN, -1);
        if (run.getId() != viewRunId || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startForeground(Constants.NOTIFICATION_ID, BackgroundLocationService.createNotification(this));
        }*/
        val latLng = LatLng(location.latitude, location.longitude)
        val endAddress = RunManager.getAddress(this, latLng)
        run.endAddress = endAddress
        val cv = ContentValues()
        cv.put(Constants.COLUMN_RUN_END_ADDRESS, endAddress)
        //Perform the update on the database and get the result
        val result = contentResolver.update(
                Uri.withAppendedPath(Constants.URI_TABLE_RUN, run.id.toString()),
                cv,
                Constants.COLUMN_RUN_ID + " = ?",
                arrayOf(run.id.toString())
        )
        //This operation should always affect only one row of the Run table, so report any result
        //other than 1 back to the UI fragments.
        if (result != 1) {
            //Create an Intent with Extras to report the results of the operation to the CombinedRunFragment
            //UI where the relevant loaders can be restarted. RunRecyclerListFragment relies on its cursor
            //loader to get this data.
            val responseIntent = Intent(Constants.SEND_RESULT_ACTION)
                    .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_UPDATE_END_ADDRESS)
                    .putExtra(Constants.ARG_RUN_ID, run.id)
                    .putExtra(Constants.EXTENDED_RESULTS_DATA, result)
                    .putExtra(Constants.UPDATED_ADDRESS_RESULT, endAddress)

            //Broadcast the Intent so that the UI can receive the result
            val receiver = mLocalBroadcastManager.sendBroadcast(responseIntent)
            if (!receiver)
                Log.i(TAG, "No receiver for Update End Date responseIntent!")
        }
        stopForeground(true)
    }

    /**
     * Handle action DeleteRuns in the provided background thread with the provided
     * parameter - ArrayList of runIds identifying the Runs to delete.
     */
    private fun handleActionDeleteRuns(runIds: ArrayList<Long>/*, ArrayList<Integer> viewsToDelete*/) {
        var runsDeleted: Long = 0
        //Keep track of number of Locations deleted
        var locationsDeleted: Long = 0
        val r = resources
        //Create a String to report the results of the deletion operation
        val stringBuilder = StringBuilder()
        val wasRunDeleted = LinkedHashMap<Long, Boolean>(runIds.size)
        //Iterate over all the items in the List selected for deletion
        for (i in runIds.indices) {
            //First, delete all the locations associated with a Run to be deleted.
            val deletedLocations = contentResolver.delete(
                    Constants.URI_TABLE_LOCATION,
                    Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                    arrayOf(runIds[i].toString())
            )
            if (deletedLocations >= 0) {
                locationsDeleted += deletedLocations.toLong()
            }
            //After deleting its Locations, delete the selected Run
            val deletedRun = contentResolver.delete(
                    Uri.withAppendedPath(Constants.URI_TABLE_RUN, runIds[i].toString()),
                    Constants.COLUMN_RUN_ID + " = ?",
                    arrayOf(runIds[i].toString())
            )
            if (deletedRun >= 0) {
                runsDeleted += deletedRun.toLong()
            }
            when (deletedRun) {
                1 -> {
                    stringBuilder.append(r.getString(R.string.delete_run_success, runIds[i]))
                    wasRunDeleted.put(runIds[i], true)
                }
                0 -> {
                    stringBuilder.append(r.getString(R.string.delete_run_failure, runIds[i]))
                    wasRunDeleted.put(runIds[i], false)
                }
                -1 -> {
                    stringBuilder.append(r.getString(R.string.delete_run_error, runIds[i]))
                    wasRunDeleted.put(runIds[i], false)
                }
                else -> {
                    stringBuilder.append(r.getString(R.string.delete_run_unexpected_return, runIds[i]))
                    wasRunDeleted.put(runIds[i], false)
                }
            }

            if (deletedLocations == -1) {
                stringBuilder.append(r.getString(R.string.delete_locations_error, runIds[i]))
            } else {
                stringBuilder.append(r.getQuantityString(R.plurals.location_deletion_results, deletedLocations, deletedLocations, runIds[i]))
            }    /*if (deletedLocations == 1) {
                stringBuilder.append("One locations associated with Run ").append(runIds.get(i)).append(" was deleted.\n\n");
            } else if (deletedLocations == 0) {
                stringBuilder.append("No locations associated with Run ").append(runIds.get(i)).append(" were deleted.\n\n");
            } else if (deletedLocations == -1) {
                stringBuilder.append("There was an error attempting to delete locations associated with Run ").append(runIds.get(i)).append(".\n\n");
            } else {
                stringBuilder.append("Unrecognized return value while attempting to delete locations associated with Run ").append(runIds.get(i)).append(".\n\n");
            }*/
        }
        stringBuilder.insert(0, r.getQuantityString(R.plurals.runs_deletion_results, runsDeleted.toInt(), runsDeleted.toInt(), locationsDeleted))
        val resultString = stringBuilder.toString()
        //Create an Intent with Extras to report the results of the operation
        //This Intent is aimed at a different Activity/Fragment, the RunRecyclerListFragment,
        //so it has a different Action specified. All the others are directed at
        //the CombinedRunFragment. The RunRecyclerListFragment needs to get this broadcast so it can
        //display the results of the delete operation in a Toast; its RecyclerView will
        //update automatically by operation of its cursor loader.
        val responseIntent = Intent(Constants.ACTION_DELETE_RUNS)
                .putExtra(Constants.EXTENDED_RESULTS_DATA, resultString)
                //.putExtra(Constants.EXTRA_VIEW_HASHMAP, shouldDeleteView);
                .putExtra(Constants.EXTRA_VIEW_HASHMAP, wasRunDeleted)
        //Broadcast the Intent so that the UI can receive the result
        val receiver = mLocalBroadcastManager.sendBroadcast(responseIntent)
        if (!receiver)
            Log.i(TAG, "No receiver for Delete Runs responseIntent!")
        stopForeground(true)
    }

    /*
     * Handle action DeleteRun in the provided background thread with the provided
     * parameter - a runId identifying a Run to delete
     */
    private fun handleActionDeleteRun(runId: Long) {

        val resultsString: String
        val builder = StringBuilder()
        val locationsDeleted = contentResolver.delete(
                Constants.URI_TABLE_LOCATION,
                Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                arrayOf(runId.toString())
        )
        when {
            locationsDeleted == -1 -> builder.append("There was an error deleting locations associated with Run ").append(runId).append(".\n")
            locationsDeleted == 0 -> builder.append("There were no locations associated with Run ").append(runId).append(" to delete.\n")
            locationsDeleted > 0 -> builder.append(locationsDeleted).append(" locations associated with Run ").append(runId).append(" were also deleted.\n")
            else -> builder.append("There was an unexpected result from the ContentProvider while attempting to delete locations for Run ").append(runId).append(".\n")
        }

        val runsDeleted = contentResolver.delete(
                Uri.withAppendedPath(Constants.URI_TABLE_RUN, runId.toString()),
                Constants.COLUMN_RUN_ID + " = ?",
                arrayOf(runId.toString())
        )
        when (runsDeleted) {
            -1 -> builder.insert(0, "There was an error attempting to delete Run $runId.\n")
            0 -> builder.insert(0, "Failed to deleted Run $runId.\n")
            1 -> builder.insert(0, "Successfully deleted Run $runId.\n")
            else -> builder.insert(0, "Unknown response from ContentProvider in attempting to delete Run $runId.\n")
        }
        resultsString = builder.toString()

        val responseIntent = Intent(Constants.ACTION_DELETE_RUN)
                .putExtra(Constants.EXTENDED_RESULTS_DATA, resultsString)
                //Put the runId here so the CombinedRunFragment of the Run being deleted can know to call
                //finish()
                .putExtra(Constants.PARAM_RUN, runId)
        val receiver = mLocalBroadcastManager.sendBroadcast(responseIntent)
        if (!receiver)
            Log.i(TAG, "No receiver for Delete Run responseIntent!")
        stopForeground(true)
    }

    companion object {
        private val TAG = "IntentService"

        /* Public static convenience methods other classes can call to start this service
     * to perform any one of its tasks.
     */

        /* Starts this service to insert a newly-created Run into the database.
     * The Run's runId field gets set by the database's insert operation.
     */
        fun startActionInsertRun(context: Context, run: Run) {
            val intent = Intent(context, TrackingLocationIntentService::class.java)
            intent.action = Constants.ACTION_INSERT_RUN
            intent.putExtra(Constants.PARAM_RUN, run)
            context.startService(intent)
        }

        /*
     *Starts this service to delete a single run. If the  service is already performing a
     *task this action will be queued.
     */

        fun startActionDeleteRun(context: Context, runId: Long) {
            val intent = Intent(context, TrackingLocationIntentService::class.java)
            intent.action = Constants.ACTION_DELETE_RUN
            intent.putExtra(Constants.PARAM_RUN_IDS, runId)
            context.startService(intent)
        }

        /* Starts this service to delete multiple Runs selected from the RunRecyclerListFragment */

        fun startActionDeleteRuns(context: Context, deleteList: ArrayList<Long>/*, ArrayList<Integer> viewsToDelete*/) {
            val intent = Intent(context, TrackingLocationIntentService::class.java)
            intent.action = Constants.ACTION_DELETE_RUNS
            intent.putExtra(Constants.PARAM_RUN_IDS, deleteList)
            //intent.putIntegerArrayListExtra(Constants.VIEWS_TO_DELETE, viewsToDelete);
            context.startService(intent)
        }

        /* Starts this service to insert a new Location associated with the Run identified by
     * the runId parameter
     */
        fun startActionInsertLocation(context: Context, runId: Long, loc: Location) {
            val intent = Intent(context, TrackingLocationIntentService::class.java)
            intent.action = Constants.ACTION_INSERT_LOCATION
            intent.putExtra(Constants.PARAM_RUN_IDS, runId)
            intent.putExtra(Constants.PARAM_LOCATION, loc)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /* Starts this service to change the Start Date of the run to the time returned by the first
     * GPS location update from the time the user presses the Start Button, which seems a more
     * accurate measure.
     */

        fun startActionUpdateStartDate(context: Context, run: Run) {
            val intent = Intent(context, TrackingLocationIntentService::class.java)
            intent.action = Constants.ACTION_UPDATE_START_DATE
            intent.putExtra(Constants.PARAM_RUN, run)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /* Starts this service to change the Starting Address of the run to the address obtained from the
     * reverse geocoding function using the first location obtained after the user starts tracking
     * the run.
     */

        fun startActionUpdateStartAddress(context: Context, run: Run, location: Location) {
            val intent = Intent(context, TrackingLocationIntentService::class.java)
            intent.action = Constants.ACTION_UPDATE_START_ADDRESS
            intent.putExtra(Constants.PARAM_RUN, run)
            intent.putExtra(Constants.PARAM_LOCATION, location)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /* Starts this service to change the Ending Address of the run to the address obtained from the
     * reverse geocoding function using the last location obtained while the user is tracking the run.
     * seconds. Largely replaced by the ScheduledThreadPoolExecutor calling UpdateEndAddressTask */

        fun startActionUpdateEndAddress(context: Context, run: Run, location: Location) {
            val intent = Intent(context, TrackingLocationIntentService::class.java)
            intent.action = Constants.ACTION_UPDATE_END_ADDRESS
            intent.putExtra(Constants.PARAM_RUN, run)
            intent.putExtra(Constants.PARAM_LOCATION, location)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
