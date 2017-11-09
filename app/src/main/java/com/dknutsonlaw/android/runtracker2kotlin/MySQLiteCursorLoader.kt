package com.dknutsonlaw.android.runtracker2kotlin

/*
  Created by dck on 9/6/15 for RunTracker2Kotlin.
  Created by dck on 2/11/15 for original RunTracker program.
  This is based upon the SQLiteCursorLoader.java file from the Big Nerd Ranch book, the source
  code from Android's CursorLoader, and some suggestions found on StackOverflow. To the version
  found in the book, I added an Uri member variable to contain a description of the SQLite table
  upon which I want to call update notifications and a ForceLoadContentObserver to register with
  the cursor to do the watching. Although this is not an implementation of a ContentProvider, its
  method of automatically loading when the underlying data store changes works, as I understand it,
  much the same way.

  This Loader also works with a ContentProvider, which is the approach adopted in September 2017.
 */


import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.support.v4.content.AsyncTaskLoader
import android.support.v4.content.Loader

internal abstract class MySQLiteCursorLoader//Pass in an Uri describing the database table that will be watched for changes
(context: Context, //Identifier for the data table we're watching
 private val mUri: Uri) : AsyncTaskLoader<Cursor>(context) {
    //ContentObserver used to watch for changes in the relevant data table
    private val mLoader = Loader<Cursor>(context)
    private val mObserver = mLoader.ForceLoadContentObserver()


    private var mCursor: Cursor? = null
    //Override loadCursor() to define the query that will return the data with which to fill the cursor.
    protected abstract fun loadCursor(): Cursor?

    override fun loadInBackground(): Cursor? {

        val cursor = loadCursor()

        if (cursor != null) {
            //Ensure the cursor window is filled
            cursor.count
            //Force a reload when the content changes
            cursor.registerContentObserver(mObserver)
            //Tell the ContentResolver the SQLite table (mUri) whose updates this loader should
            //be notified of
            cursor.setNotificationUri(context.contentResolver, mUri)

        }
        return cursor
    }

    //Runs on the UI thread
    override fun deliverResult(cursor: Cursor?) {
        if (isReset) {
            //An async query came in while the loader is stopped
            cursor?.close()
            return
        }
        val oldCursor = mCursor
        mCursor = cursor

        if (isStarted) {
            super.deliverResult(cursor)
        }

        if (oldCursor != null && oldCursor != cursor && !oldCursor.isClosed) {
            oldCursor.close()
        }
    }

    /* Starts an asynchronous load of the SQLite database data. When the result is ready, the
     * callbacks will be called on the UI thread. If a previous load has been completed and is
     * still valid the result may be passed to the callbacks immediately. Must be called from
     * the UI thread.*/
    override fun onStartLoading() {
        if (mCursor != null) {
            deliverResult(mCursor)
        }
        if (takeContentChanged() || mCursor == null) {
            forceLoad()
        }
    }

    //Must be called from the UI thread
    override fun onStopLoading() {
        //Attempt to cancel the current load task if possible
        cancelLoad()
    }

    override fun onCanceled(cursor: Cursor?) {
        if (cursor != null && !cursor.isClosed) {
            cursor.close()
        }
    }

    override fun onReset() {
        super.onReset()

        //Ensure the loader is stopped
        onStopLoading()

        if (mCursor != null && !mCursor!!.isClosed) {
            mCursor!!.close()
        }
        mCursor = null
    }

    companion object {
        private val TAG = "MySQLiteCursorLoader"
    }
}
