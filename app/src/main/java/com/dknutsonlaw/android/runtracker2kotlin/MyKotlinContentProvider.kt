/* Implementation of a ContentProvider that will allow the app to be used with Android Oreo, which
 * will not run using the former technique of directly addressing SQL Tables.
 */

package com.dknutsonlaw.android.runtracker2kotlin

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.util.Log

class MyKotlinContentProvider : ContentProvider() {

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        /*Individual Runs and their associated Location Lists are all we ever delete, so those are
         *the only two cases we need deal with.
         */
        // Implement this to handle requests to delete one or more rows.
        val result: Int

        when (uriMatcher.match(uri)) {
            Constants.LOCATION_LIST -> {
                result = sHelper!!.writableDatabase.delete(
                        Constants.TABLE_LOCATION,
                        selection,
                        selectionArgs
                )
                try {

                    context!!.contentResolver.notifyChange(Constants.URI_TABLE_LOCATION,
                            null)
                } catch (npe: NullPointerException) {
                    Log.e(TAG, "Caught an NPE while trying to get the ContentResolver")
                }
            }

            Constants.SINGLE_RUN -> {
                result = sHelper!!.writableDatabase.delete(
                        Constants.TABLE_RUN,
                        selection,
                        selectionArgs
                )
                try {

                    context!!.contentResolver.notifyChange(Constants.URI_TABLE_RUN, null)
                } catch (npe: NullPointerException) {
                    Log.e(TAG, "Caught an NPE while trying to get the ContentResolver")
                }
            }

            else -> {
                Log.e(TAG, "You must have goofed up somewhere - bad URI in call to ContentProvider.delete()")
                result = -1
            }
        }
        return result
    }

    /*    if (uriMatcher.match(uri) == Constants.LOCATION_LIST) {
            result = sHelper!!.writableDatabase.delete(
                    Constants.TABLE_LOCATION,
                    selection,
                    selectionArgs
            )
            try {

                context!!.contentResolver.notifyChange(Constants.URI_TABLE_LOCATION,
                        null)
            } catch (npe: NullPointerException) {
                Log.e(TAG, "Caught an NPE while trying to get the ContentResolver")
            }

        } else if (uriMatcher.match(uri) == Constants.SINGLE_RUN) {

            result = sHelper!!.writableDatabase.delete(
                    Constants.TABLE_RUN,
                    selection,
                    selectionArgs
            )
            try {

                context!!.contentResolver.notifyChange(Constants.URI_TABLE_RUN, null)
            } catch (npe: NullPointerException) {
                Log.e(TAG, "Caught an NPE while trying to get the ContentResolver")
            }

        } else {
            Log.e(TAG, "You must have goofed up somewhere - bad URI in call to ContentProvider.delete()")
            result = -1
        }
        return result
    }*/

    override fun getType(uri: Uri): String? {

        return when (uriMatcher.match(uri)) {
            Constants.RUN_LIST -> "vnd.android.cursor.dir/vnd.runtracker2kotlin.run"
            Constants.SINGLE_RUN -> "vnd.android.cursor.item/vnd.runtracker2kotlin.run"
            Constants.LOCATION_LIST -> "vnd.android.cursor.dir/vnd.runtracker2kotlin.location"
            Constants.SINGLE_LOCATION -> "vnd.android.cursor.item/vnd.runtracker2kotlin.location"
            else -> {
                Log.e(TAG, "Error in attempt to getType()")
                null
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        var results: Long = -1
        when (uriMatcher.match(uri)) {
            Constants.RUN_LIST -> {
                results = sHelper!!.writableDatabase.insert(
                        Constants.TABLE_RUN, null,
                        values
                )
                try {

                    context!!.contentResolver.notifyChange(Constants.URI_TABLE_RUN, null)
                } catch (npe: NullPointerException) {
                    Log.e(TAG, "Caught an NPE while trying to get the ContentResolver")
                }

            }
            Constants.LOCATION_LIST -> {
                results = sHelper!!.writableDatabase.insert(
                        Constants.TABLE_LOCATION, null,
                        values
                )
                try {

                    context!!.contentResolver.notifyChange(Constants.URI_TABLE_LOCATION, null)
                    Log.i(TAG, "Called notifyChange on TABLE_LOCATION on insertion of location")
                } catch (npe: NullPointerException) {
                    Log.e(TAG, "Caught an NPE while trying to get the ContentResolver")
                }

            }
        }
        return ContentUris.withAppendedId(uri, results)
    }

    override fun onCreate(): Boolean {

        sHelper = RunDatabaseHelper(context)
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
                       selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val cursor: Cursor?

        when (uriMatcher.match(uri)) {

            Constants.RUN_LIST -> {
                val numericSortOrder = Integer.parseInt(sortOrder)
                when (numericSortOrder) {
                    Constants.SORT_BY_DATE_ASC -> cursor = sHelper!!.readableDatabase.query(
                            Constants.TABLE_RUN, null, null, null, null, null,
                            Constants.COLUMN_RUN_START_DATE + " asc"
                    )
                    Constants.SORT_BY_DATE_DESC -> cursor = sHelper!!.readableDatabase.query(
                            Constants.TABLE_RUN, null, null, null, null, null,
                            Constants.COLUMN_RUN_START_DATE + " desc"
                    )
                    Constants.SORT_BY_DISTANCE_ASC -> cursor = sHelper!!.readableDatabase.query(
                            Constants.TABLE_RUN, null, null, null, null, null,
                            Constants.COLUMN_RUN_DISTANCE + " asc"
                    )
                    Constants.SORT_BY_DISTANCE_DESC -> cursor = sHelper!!.readableDatabase.query(
                            Constants.TABLE_RUN, null, null, null, null, null,
                            Constants.COLUMN_RUN_DISTANCE + " desc"
                    )
                    Constants.SORT_BY_DURATION_ASC -> cursor = sHelper!!.readableDatabase.query(
                            Constants.TABLE_RUN, null, null, null, null, null,
                            Constants.COLUMN_RUN_DURATION + " asc"
                    )
                    Constants.SORT_BY_DURATION_DESC -> cursor = sHelper!!.readableDatabase.query(
                            Constants.TABLE_RUN, null, null, null, null, null,
                            Constants.COLUMN_RUN_DURATION + " desc"
                    )
                    Constants.SORT_NO_RUNS -> cursor = sHelper!!.readableDatabase.query(
                            Constants.TABLE_RUN, null, null, null, null, null, null
                    )
                    else -> {
                        cursor = null
                        Log.e(TAG, "Sort Order Error")
                    }
                }
                try {

                    cursor!!.setNotificationUri(context!!.contentResolver,
                            Constants.URI_TABLE_RUN)
                } catch (npe: NullPointerException) {
                    Log.e(TAG, "Caught an npe while trying to setNotificationUri")
                }

            }
            Constants.SINGLE_RUN -> {
                cursor = sHelper!!.readableDatabase.query(
                        Constants.TABLE_RUN, null,
                        selection,
                        selectionArgs, null, null, null
                )

                cursor!!.setNotificationUri(context!!.contentResolver,
                        Constants.URI_TABLE_RUN)
            }
            Constants.LOCATION_LIST -> {
                cursor = sHelper!!.readableDatabase.query(
                        Constants.TABLE_LOCATION, null,
                        selection,
                        selectionArgs, null, null,
                        Constants.COLUMN_LOCATION_TIMESTAMP + " asc"
                )
                try {

                    cursor!!.setNotificationUri(context!!.contentResolver,
                            Constants.URI_TABLE_LOCATION)
                } catch (npe: NullPointerException) {
                    Log.e(TAG, "Caught an NPE while trying to set Notification Uri")
                }

            }
            Constants.SINGLE_LOCATION -> {
                cursor = sHelper!!.readableDatabase.query(
                        Constants.TABLE_LOCATION, null,
                        selection,
                        selectionArgs, null, null,
                        sortOrder,
                        "1"
                )

                cursor!!.setNotificationUri(context!!.contentResolver,
                        Constants.URI_TABLE_LOCATION)
            }
            else -> {
                cursor = null
                Log.i(TAG, "Invalid Uri passed to ContentProvider")
            }
        }
        return cursor
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<String>?): Int {
        val results: Int

        when (uriMatcher.match(uri)) {
            Constants.SINGLE_RUN -> {
                results = sHelper!!.writableDatabase.update(
                        Constants.TABLE_RUN,
                        values,
                        selection,
                        selectionArgs
                )
                try {

                    context!!.contentResolver.notifyChange(Constants.URI_TABLE_RUN, null)
                } catch (npe: NullPointerException) {
                    Log.e(TAG, "Caught an NPE while trying to get the ContentResolver")
                }

            }
            else -> {
                results = -1
                Log.e(TAG, "Updates should only happen to Single Runs")
            }
        }
        return results
    }

    companion object {

        private val TAG = "MyKotlinContentProvider"

        private var sHelper: RunDatabaseHelper? = null

        private val uriMatcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            uriMatcher.addURI(Constants.AUTHORITY, "run", Constants.RUN_LIST)
            uriMatcher.addURI(Constants.AUTHORITY, "run/#", Constants.SINGLE_RUN)
            uriMatcher.addURI(Constants.AUTHORITY, "location", Constants.LOCATION_LIST)
            uriMatcher.addURI(Constants.AUTHORITY, "location/#", Constants.SINGLE_LOCATION)
        }
    }
}
