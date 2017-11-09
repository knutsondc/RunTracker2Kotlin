package com.dknutsonlaw.android.runtracker2kotlin.ui

/*
  Created by dck on 9/6/15. An adapter that feeds fragments to a ViewPager based upon data taken
  from a database cursor.
 */
import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.PagerAdapter
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.ViewGroup

import java.util.HashMap

abstract class CursorFragmentStatePagerAdapter(context: Context, fm: FragmentManager, cursor: Cursor) : FragmentStatePagerAdapter(fm) {

    private var mDataValid: Boolean = false
    var cursor: Cursor? = null
        protected set
    private var mContext: Context = context
    private var mRegisteredFragments: SparseArray<Any> = SparseArray<Any>()
    private var mItemPositions: SparseIntArray? = null
    private var mRunIdToFragment: SparseArray<Any> = SparseArray<Any>()
    private var mObjectMap: HashMap<Any, Int> = HashMap()
    private var mRowIDColumn: Int = 0

    init {

        init(context, cursor)
    }

    private fun init(context: Context, c: Cursor?) {
        //mObjectMap = HashMap()
        //mRegisteredFragments = SparseArray<Any>()
        //mRunIdToFragment = SparseArray<Any>()
        val cursorPresent = c != null
        cursor = c
        mDataValid = cursorPresent
        //mContext = context
        mRowIDColumn = if (cursorPresent) c!!.getColumnIndexOrThrow(BaseColumns._ID) else -1
    }

    override fun getItemPosition(`object`: Any): Int {
        val rowId = mObjectMap[`object`]
        return if (rowId != null && mItemPositions != null) {
            mItemPositions!!.get(rowId, PagerAdapter.POSITION_NONE)
        } else PagerAdapter.POSITION_NONE
    }

    //Create a SparseArray to associate item ID with its position in the adapter
    private fun setItemPositions() {
        mItemPositions = null

        if (mDataValid) {
            val count = cursor!!.count
            mItemPositions = SparseIntArray(count)
            cursor!!.moveToPosition(-1)
            while (cursor!!.moveToNext()) {
                val rowId = cursor!!.getInt(mRowIDColumn)
                val cursorPos = cursor!!.position
                mItemPositions!!.append(rowId, cursorPos)
            }
        }
    }

    override fun getItem(position: Int): Fragment? {

        when (mDataValid) {

            true -> {
                cursor!!.moveToPosition(position)
                return getItem(mContext, cursor!!)
            }
            false -> return null
        }

        /*if (mDataValid) {
            cursor!!.moveToPosition(position)
            return getItem(mContext, cursor!!)
        } else {
            return null
        }*/
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val rowId = mObjectMap[`object`]
        mObjectMap.remove(`object`)
        mRegisteredFragments.remove(position)
        mRunIdToFragment.remove(rowId!!)
        super.destroyItem(container, position, `object`)
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        if (!mDataValid) {
            throw IllegalStateException("this should only be called when the cursor is valid")
        }
        if (!cursor!!.moveToPosition(position)) {
            throw IllegalStateException("couldn't move cursor to position " + position)
        }

        val rowId = cursor!!.getInt(mRowIDColumn)
        val obj = super.instantiateItem(container, position)
        //instantiate mappings of object to ID number and object (fragment) to adapter position
        mObjectMap.put(obj, rowId)
        mRegisteredFragments.put(position, obj)
        mRunIdToFragment.put(rowId, obj)


        return obj
    }

    //Get fragment instance from specified position in adapter
    fun getRegisteredFragment(position: Int): Any = mRegisteredFragments.get(position)

    //Get fragment instance associated with RunId
    fun getFragmentFromRunId(runId: Long): Any = mRunIdToFragment.get(runId.toInt())

    abstract fun getItem(context: Context, cursor: Cursor): Fragment?

    override fun getCount(): Int {
        return if (mDataValid) {
            cursor!!.count
        } else {
            0
        }
    }

    fun changeCursor(cursor: Cursor) {
        val old = swapCursor(cursor)
        old?.close()
    }

    fun swapCursor(newCursor: Cursor?): Cursor? {
        if (newCursor === cursor) {
            return null
        }
        val oldCursor = cursor
        cursor = newCursor
        if (newCursor != null) {
            mRowIDColumn = newCursor.getColumnIndexOrThrow(BaseColumns._ID)
            mDataValid = true
        } else {
            mRowIDColumn = -1
            mDataValid = false
        }

        setItemPositions()
        if (mDataValid) {
            notifyDataSetChanged()
        }


        return oldCursor
    }

}