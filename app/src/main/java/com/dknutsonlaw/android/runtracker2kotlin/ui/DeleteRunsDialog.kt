package com.dknutsonlaw.android.runtracker2kotlin.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import com.dknutsonlaw.android.runtracker2kotlin.Constants
import com.dknutsonlaw.android.runtracker2kotlin.R

/**
 * Created by dck on 11/9/16. A simple AlertDialog asking the user to confirm deletion of a Run or Runs.
 * Takes two fragment arguments: which Activity the request for the dialog came from; and how many Runs
 * are marked for deletion. The former is needed so that only the Activity that made the request will
 * act when the dialog calls back (all the Activities implement the interface that sets up the
 * callbacks so there has to be a way to prevent those that didn't invoke the dialog from trying to
 * act on it); the latter is needed by the dialog to make the dialog's message fit the number of Runs
 * marked for deletion.
 */

class DeleteRunsDialog : DialogFragment() {

    private var mNumberOfRuns: Int = 0
    private var mWhichFragment: Int = 0
    //Use this instance of interface to communicate with UI fragments
    private var mListener: DeleteRunsDialogListener? = null

    //Interface to communicate to relevant UI fragment confirmation of whether to delete Runs.
    interface DeleteRunsDialogListener {
        fun onDeleteRunsDialogPositiveClick(which: Int)
        fun onDeleteRunsDialogNegativeClick(which: Int)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bundle = arguments
        mNumberOfRuns = bundle?.getInt(Constants.NUMBER_OF_RUNS) ?: 0
        mWhichFragment = bundle!!.getInt(Constants.FRAGMENT)
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        //verify that the Activity implements the necessary interface
        try {
            mListener = context as DeleteRunsDialogListener?
        } catch (e: ClassCastException) {
            throw ClassCastException(context!!.toString() + " must implement DeleteRunsDialogListener")
        }

    }

    override fun onCreateDialog(bundle: Bundle?): Dialog {
        //Build the Dialog and set up the button click handlers
        val r = activity!!.resources
        val builder = AlertDialog.Builder(activity!!)
        builder.setMessage(r.getQuantityString(R.plurals.deletion_dialog_message, mNumberOfRuns, mNumberOfRuns))
                .setPositiveButton(r.getText(android.R.string.ok)) { _, _ ->
                    //Send the positive button result back to the host activity
                    mListener!!.onDeleteRunsDialogPositiveClick(mWhichFragment)
                }
                .setNegativeButton(r.getText(android.R.string.no)) { _, _ ->
                    //Send the negative button result back to the host activity
                    mListener!!.onDeleteRunsDialogNegativeClick(mWhichFragment)
                }
        return builder.create()
    }
}
