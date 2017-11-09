package com.dknutsonlaw.android.runtracker2kotlin

import android.support.v4.app.Fragment
import android.util.Log

/**
 * Created by dck on 10/28/15. Activity to host a Fragment displaying a RecyclerView displaying all
 * the recorded Runs.
 */
class RunRecyclerListActivity : SingleFragmentActivity(), DeleteRunsDialog.DeleteRunsDialogListener {

    override fun createFragment(): Fragment = RunRecyclerListFragment()

    override fun onDeleteRunsDialogPositiveClick(which: Int) {
        /*Check to see if this call is for us and, if so, forward to the fragment's
         *onDeleteRunsDialogPositiveClick method.
         */
        Log.i(TAG, "Reached RunRecyclerListActivity PositiveClick callback")
        if (which == Constants.RUN_LIST_RECYCLER_FRAGMENT) {
            val fragment = supportFragmentManager
                    .findFragmentById(R.id.fragmentContainer) as RunRecyclerListFragment
            fragment.onDeleteRunsDialogPositiveClick()
        }
    }

    override fun onDeleteRunsDialogNegativeClick(which: Int) {
        /*Check to see if this call is for us and, if so, forward to the fragment's
         *onDeleteRunsDialogNegativeClick method.
         */
        Log.i(TAG, "Reached RunRecyclerListActivity NegativeClick callback")
        if (which == Constants.RUN_LIST_RECYCLER_FRAGMENT) {
            val fragment = supportFragmentManager
                    .findFragmentById(R.id.fragmentContainer) as RunRecyclerListFragment
            fragment.onDeleteRunsDialogNegativeClick()
        }
    }

    companion object {

        private val TAG = "RunRecyclerListActivity"
    }
}
