/*Cursor Loader designed to return a single Run from the Run table in the database.
 *
 */

package com.dknutsonlaw.android.runtracker2kotlin

import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * Created by dck on 11/14/15. Created to replace a loader that simply returns a run object
 */
internal class RunCursorLoader(context: Context, private val mRunId: Long?) : MySQLiteCursorLoader(context, Constants.URI_TABLE_RUN) {

    override fun loadCursor(): Cursor? {

        return context.contentResolver.query(
                Uri.withAppendedPath(Constants.URI_TABLE_RUN, mRunId.toString()),
                null,
                Constants.COLUMN_RUN_ID + " = ?",
                arrayOf(mRunId.toString()),
                null
        )
    }

    companion object {
        private val TAG = "runCursorLoader"
    }
}
