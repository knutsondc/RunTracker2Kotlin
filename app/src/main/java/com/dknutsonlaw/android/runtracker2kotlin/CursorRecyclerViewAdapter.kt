package com.dknutsonlaw.android.runtracker2kotlin

/*
  Adapted by dck on 10/28/15. An adapter that extends the RecyclerView.Adapter for use with a
  cursor.
 */
import android.content.Context
import android.database.Cursor
import android.support.v7.widget.RecyclerView

/**
 * Created by skyfishjy on 10/31/14.
 */

abstract class CursorRecyclerViewAdapter<VH : RecyclerView.ViewHolder>(context: Context, cursor: Cursor?) : RecyclerView.Adapter<VH>() {

    var cursor: Cursor? = null
        private set

    private var mDataValid: Boolean = false

    private var mRowIdColumn: Int = 0

    init {
        this.cursor = cursor
        mDataValid = cursor != null
        mRowIdColumn = if (mDataValid) this.cursor!!.getColumnIndexOrThrow("_id") else -1
    }

    override fun getItemCount(): Int {
        return if (mDataValid && cursor != null) {
            cursor!!.count
        } else 0
    }

    override fun getItemId(position: Int): Long {
        return if (hasStableIds() && mDataValid && cursor != null && cursor!!.moveToPosition(position)) {
            cursor!!.getLong(mRowIdColumn)
        } else RecyclerView.NO_ID
    }

    abstract fun onBindViewHolder(viewHolder: VH, cursor: Cursor)

    override fun onBindViewHolder(viewHolder: VH, position: Int) {
        if (!mDataValid) {
            throw IllegalStateException("this should only be called when the cursor is valid")
        }
        if (!cursor!!.moveToPosition(position)) {
            throw IllegalStateException("couldn't move cursor to position " + position)
        }
        onBindViewHolder(viewHolder, cursor!!)
    }

    /**
     * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
     * closed.
     */
    fun changeCursor(cursor: Cursor) {
        val old = swapCursor(cursor)
        old?.close()
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * [.changeCursor], the returned old Cursor is *not*
     * closed.
     */
    fun swapCursor(newCursor: Cursor): Cursor? {
        if (newCursor === cursor) {
            return null
        }
        val oldCursor = cursor

        cursor = newCursor
        if (cursor != null) {
            /*if (mDataSetObserver != null) {
                mCursor.registerDataSetObserver(mDataSetObserver);
            }*/
            mRowIdColumn = newCursor.getColumnIndexOrThrow("_id")
            mDataValid = true
            notifyDataSetChanged()
        } else {
            mRowIdColumn = -1
            mDataValid = false
            notifyItemRangeRemoved(0, itemCount)
        }
        return oldCursor
    }
}
