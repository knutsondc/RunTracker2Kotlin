package com.dknutsonlaw.android.runtracker2kotlin

/*
  Created by dck on 9/6/15 for RunTracker2Kotlin.
  Created by dck on 2/12/15 for original RunTracker program..
  This loader needs only to take the Id of the run we're tracking to feed into the query into the
  database, so we need only provide a constructor and override the loadCursor method. Note that
  it extends MySQLiteCursorLoader which supports automatic updating upon change in the relevant
  database table.

  8/12/15 - No longer used in RunFragment; RunFragment only displays the location data for the
  first and last locations for a run, so we really don't need a cursor holding ALL the locations
  for a run. This has been replaced by a LastLocationLoader.
 */
import android.content.Context
import android.database.Cursor

internal class MyLocationListCursorLoader(c: Context, private val mRunId: Long?) : MySQLiteCursorLoader(c, Constants.URI_TABLE_LOCATION) {

    override fun loadCursor(): Cursor? {

        //return RunManager.queryLocationsForRun(mRunId);
        return context.contentResolver.query(
                Constants.URI_TABLE_LOCATION,
                null,
                Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                Array<String>(1, {mRunId.toString()}),
                Constants.COLUMN_LOCATION_TIMESTAMP + "desc"
        )
    }

    companion object {
        private val TAG = "MyLocationListCursorLoader"
    }
}
