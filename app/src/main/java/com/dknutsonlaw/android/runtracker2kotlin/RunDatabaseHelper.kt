package com.dknutsonlaw.android.runtracker2kotlin

/*
  Created by dck on 9/6/15. The class that creates the database used to track Runs and their associated Locations and
  implements basic database CRUD functions needed to implement the program.
 */

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.net.Uri
import android.util.Log

import java.util.Date

/**
 * Created by dck on 1/14/15.
 * The class that creates the database used to track Runs and their associated Locations and
 * implements basic database CRUD functions needed to implement the program.
 *
 * 2/18/15 - Completed work on Intent Service so that all database write operations run on the
 * Intent Service's thread, while all database read operations run on Loader threads.
 *
 * 8/12/15 - Changed handling of inserting locations so that notifyChange() is called on both
 * database tables from here so that live updates of tracked runs occur in the ListView.
 *
 * 8/14/15 - Added Run Table columns for starting and ending addresses and related changes to
 * methods.
 */
class RunDatabaseHelper(context: Context) : SQLiteOpenHelper(context, Constants.DB_NAME, null, Constants.VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        //Create the "run" table
        db.execSQL("create table run (" +
                "_id integer primary key autoincrement, start_date integer, end_address varchar(100), " +
                "start_address varchar(100), distance real, duration integer)")
        //Create the "location" table
        db.execSQL("create table location (" +
                " timestamp integer, latitude real, longitude real, altitude real," +
                " provider varchar(100), run_id integer references run(_id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        //implement if database schema changes
    }

    fun insertRun(context: Context, cv: ContentValues): Long {
        val results: Long

        val run = Run()
        cv.put(Constants.COLUMN_RUN_START_DATE, run.startDate!!.time)
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.startAddress)
        cv.put(Constants.COLUMN_RUN_END_ADDRESS, run.endAddress)
        cv.put(Constants.COLUMN_RUN_DISTANCE, run.distance)
        cv.put(Constants.COLUMN_RUN_DURATION, run.duration)
        Log.i(TAG, "cv values in insertRun for Run " + run.id + ": " + cv.toString())
        results = writableDatabase.insert(Constants.TABLE_RUN, null, cv)
        run.id = results
        context.contentResolver.notifyChange(Constants.URI_TABLE_RUN, null)
        Log.i(TAG, "Called notifyChange on URI_TABLE_RUN in insertRun for Run " + results)
        return results
    }

    /*This function is needed to update the Run Table so that the StartDate field is equal to
    * the timestamp of the Run's first location. The Run is updated in memory in RunFragment and
    * is then routed here through the updateStartDateTask Runnable.
    */
    fun updateRunStartDate(context: Context, run: Run): Int {
        val cv = ContentValues()
        cv.put(Constants.COLUMN_RUN_START_DATE, run.startDate!!.time)
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.startAddress)

        val result = writableDatabase.update(Constants.TABLE_RUN,
                cv,
                Constants.COLUMN_RUN_ID + " =?",
                arrayOf(run.id.toString()))
        if (result == 1) {
            Log.i(TAG, "In updateRunStartDate(), successfully updated Start Address for Run "
                    + run.id + "to " + run.startAddress)
        }
        context.contentResolver.notifyChange(Constants.URI_TABLE_RUN, null)
        Log.i(TAG, "Called notifyChange on TABLE_RUN from updateRunStartDate() for Run " + run.id)
        return result
    }

    /*This function is needed to update the Run Table with the Starting Address value derived from
    * the first location received after the user presses the Start button in the RunFragment
    * UI
    */
    fun updateStartAddress(context: Context, run: Run): Int {
        Log.i(TAG, "Entering updateStartAddress(), run.getStartAddress() for Run " +
                run.id + " is: " + run.startAddress)
        val cv = ContentValues()
        cv.put(Constants.COLUMN_RUN_START_ADDRESS, run.startAddress)
        Log.i(TAG, "ContentValues in updateStartAddress() for Run " +
                run.id + ": " + cv.toString())
        val result = writableDatabase.update(Constants.TABLE_RUN,
                cv,
                Constants.COLUMN_RUN_ID + " =?",
                arrayOf(run.id.toString()))
        context.contentResolver.notifyChange(Constants.URI_TABLE_RUN, null)
        Log.i(TAG, "Called notifyChange on TABLE_RUN from updateStartAddress() for Run " + run.id)
        return result
    }

    /*This function is needed to update the Run Table with the Ending Address value derived from
    * the last location received when an EndAddressUpdateTask runs or when  the user presses the
    * Stop button in the RunFragment UI.
    */
    fun updateEndAddress(context: Context, run: Run): Int {
        Log.i(TAG, "Entering updateEndAddress() for Run " + run.id +
                " run.getEndAddress() is: " + run.endAddress)
        val cv = ContentValues()
        cv.put(Constants.COLUMN_RUN_END_ADDRESS, run.endAddress)
        Log.i(TAG, "ContentValues for Run " + run.id +
                " in updateEndAddress(): " + cv.toString())
        val result = writableDatabase.update(Constants.TABLE_RUN,
                cv,
                Constants.COLUMN_RUN_ID + " =?",
                arrayOf(run.id.toString()))
        context.contentResolver.notifyChange(Constants.URI_TABLE_RUN, null)
        Log.i(TAG, "Called notifyChange on TABLE_RUN from updateEndAddress() for Run " + run.id)
        return result
    }

    fun insertLocation(uri: Uri, values: ContentValues): Long = writableDatabase.insert(Constants.TABLE_LOCATION, null, values)


    //Return the number of locations associated with a given Run
    fun getRunLocationCount(runId: Long): Long {
        val db = readableDatabase
        return DatabaseUtils.queryNumEntries(db, Constants.TABLE_LOCATION,
                Constants.COLUMN_LOCATION_RUN_ID + " =?",
                arrayOf(runId.toString()))
    }

    companion object {
        private val TAG = "RunDatabaseHelper"

        /**
         * Returns a Run object configured for the current row,
         * or null if the current row is invalid.
         */
        fun getRun(cursor: Cursor): Run? {
            if (cursor.isBeforeFirst || cursor.isAfterLast)
                return null
            val run = Run()

            val runId = cursor.getLong(cursor.getColumnIndex(Constants.COLUMN_RUN_ID))
            run.id = runId
            val startDate = cursor.getLong(cursor.getColumnIndex(Constants.COLUMN_RUN_START_DATE))
            run.startDate = Date(startDate)
            val startAddress = cursor.getString(cursor.getColumnIndex(
                    Constants.COLUMN_RUN_START_ADDRESS))
            run.startAddress = startAddress
            val distance = cursor.getDouble(cursor.getColumnIndex(Constants.COLUMN_RUN_DISTANCE))
            run.distance = distance
            val duration = cursor.getLong(cursor.getColumnIndex(Constants.COLUMN_RUN_DURATION))
            run.duration = duration
            val endAddress = cursor.getString(cursor.getColumnIndex(
                    Constants.COLUMN_RUN_END_ADDRESS))
            run.endAddress = endAddress
            return run
        }

        fun getLocation(cursor: Cursor): Location? {
            if (cursor.isBeforeFirst || cursor.isAfterLast) {
                return null
            }

            //First get the provider out so you can use the constructor
            val provider = cursor.getString(cursor.getColumnIndex(Constants.COLUMN_LOCATION_PROVIDER))
            val loc = Location(provider)
            //Populate the remaining properties
            loc.longitude = cursor.getDouble(cursor.getColumnIndex(Constants.COLUMN_LOCATION_LONGITUDE))
            loc.latitude = cursor.getDouble(cursor.getColumnIndex(Constants.COLUMN_LOCATION_LATITUDE))
            loc.altitude = cursor.getDouble(cursor.getColumnIndex(Constants.COLUMN_LOCATION_ALTITUDE))
            loc.time = cursor.getLong(cursor.getColumnIndex(Constants.COLUMN_LOCATION_TIMESTAMP))
            return loc


        }
    }
}
