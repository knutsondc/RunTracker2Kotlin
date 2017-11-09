package com.dknutsonlaw.android.runtracker2kotlin

/*
  Created by dck on 9/6/15. A loader for a cursor holding data for a list of runs that automatically
  updates as the underlying data changes.
 */
import android.content.Context
import android.database.Cursor
import android.util.Log

internal class RunListCursorLoader(context: Context, private val mSortOrder: Int) : MySQLiteCursorLoader(context, Constants.URI_TABLE_RUN) {

    override fun loadCursor(): Cursor? {
        /*Query the list of all runs in the database; return different cursor
         *depending upon the sort order selected in the loader's constructor.
         */
        val cursor: Cursor?
        when (mSortOrder) {
            Constants.SORT_BY_DATE_ASC -> cursor = context.contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DATE_ASC.toString()
            )
            Constants.SORT_BY_DATE_DESC -> cursor = context.contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DATE_DESC.toString()
            )
            Constants.SORT_BY_DISTANCE_ASC -> cursor = context.contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DISTANCE_ASC.toString()
            )
            Constants.SORT_BY_DISTANCE_DESC -> cursor = context.contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DISTANCE_DESC.toString()
            )
            Constants.SORT_BY_DURATION_ASC -> cursor = context.contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DURATION_ASC.toString()
            )
            Constants.SORT_BY_DURATION_DESC -> cursor = context.contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DURATION_DESC.toString()
            )
            Constants.SORT_NO_RUNS -> {

                cursor = context.contentResolver.query(
                        Constants.URI_TABLE_RUN, null, null, null, null
                )
                /*Log.i(TAG, "How'd you get here?!?")
                cursor = null*/
            }
            else -> {
                Log.i(TAG, "How'd you get here?!?")
                cursor = null
            }
        }
        return cursor
    }

    companion object {
        private val TAG = ".runlistcursorloader"
    }
}
