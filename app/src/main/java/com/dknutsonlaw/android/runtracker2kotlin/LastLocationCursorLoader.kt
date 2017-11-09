package com.dknutsonlaw.android.runtracker2kotlin

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * Created by dck on 11/14/15. Created to replace a loader that simply returned the location object
 * directly
 */

internal class LastLocationCursorLoader(c: Context, private val mRunId: Long) : MySQLiteCursorLoader(c, Constants.URI_TABLE_LOCATION) {

    override fun loadCursor(): Cursor? {
        Log.i(TAG, "In loadCursor in LastLocationCursorLoader")
        return context.contentResolver.query(
                Uri.withAppendedPath(Constants.URI_TABLE_LOCATION, mRunId.toString()),
                null,
                Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                arrayOf(mRunId.toString()),
                Constants.SORT_BY_DATE_DESC.toString()
        )
    }

    companion object {
        private val TAG = "lastLocCursorLoader"
    }
}
