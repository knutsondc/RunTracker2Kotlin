package com.dknutsonlaw.android.runtracker2kotlin.ui

/*
  Created by dck on 10/28/15. A Fragment to display a RecyclerView showing all the Runs recorded
  utilizing a loader serving up a cursor holding data concerning all the Runs in the database.
 */

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
//import android.content.ComponentName;
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
//import android.content.IntentSender;
//import android.content.ServiceConnection;
import android.content.ServiceConnection
import android.database.Cursor
import android.os.Bundle
//import android.os.Handler;
//import android.os.IBinder;
//import android.os.Message;
//import android.os.Messenger;
//import android.os.RemoteException;
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback
import com.bignerdranch.android.multiselector.MultiSelector
import com.bignerdranch.android.multiselector.SwappingHolder
import com.dknutsonlaw.android.runtracker2kotlin.BackgroundLocationService
import com.dknutsonlaw.android.runtracker2kotlin.Constants
import com.dknutsonlaw.android.runtracker2kotlin.R
import com.dknutsonlaw.android.runtracker2kotlin.Run
import com.dknutsonlaw.android.runtracker2kotlin.RunDatabaseHelper
import com.dknutsonlaw.android.runtracker2kotlin.RunListCursorLoader
import com.dknutsonlaw.android.runtracker2kotlin.RunManager
import com.dknutsonlaw.android.runtracker2kotlin.RunTracker2Kotlin
//import com.google.android.gms.common.ConnectionResult;
//import com.google.android.gms.common.api.GoogleApiClient;

//import java.lang.ref.WeakReference;
import java.util.ArrayList
import java.util.LinkedHashMap

class RunRecyclerListFragment : Fragment(), LoaderManager.LoaderCallbacks<Cursor> {

    private var mIntentFilter: IntentFilter? = null
    private var mResultsReceiver: ResultsReceiver? = null
    private var mOptionsMenu: Menu? = null
    //Default sort order is most recent first
    private var mSortOrder = Constants.SORT_BY_DATE_DESC
    private var mSubtitle: String? = null
    private var mRunListRecyclerView: RecyclerView? = null
    private var mAdapter: RunRecyclerListAdapter? = null
    private var mEmptyViewTextView: TextView? = null
    private var mEmptyViewButton: Button? = null
    private var mService: BackgroundLocationService? = null
    private var mBound = false
    private var mDeleteList: List<Int>? = null
    private var mActionMode: ActionMode? = null
    //Are we newly opening this fragment or are we coming back from RunPagerActivity?
    private var mFirstVisit: Boolean = false

    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            val binder = iBinder as BackgroundLocationService.LocationBinder
            mService = binder.service
            mBound = true
            Log.i(TAG, "BackgroundLocationService connected")
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mService = null
            mBound = false
            Log.i(TAG, "BackgroundLocationService disconnected.")
        }
    }

    /*Using Big Nerd Ranch MultiSelector library for RecyclerView to enable multiselect for deletion
     *of Runs.
     */
    private val mMultiSelector = MultiSelector()
    /*Callback invoked upon long click on RunHolder that creates an ActionMode used for deletion of
     *the selected Runs.
     */
    private val mDeleteMode = object : ModalMultiSelectorCallback(mMultiSelector) {
        override fun onCreateActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
            //Create an ActionMode menu with a Delete item
            super.onCreateActionMode(actionMode, menu)
            activity!!.menuInflater.inflate(R.menu.run_list_item_context, menu)
            return true
        }

        /*The action mode simply gathers the Runs selected for deletion into a List<Integer> member
         *variable. If the confirmation dialog confirms deletion, deleteRuns is called,
         *deletes one-by-one the Runs selected using the member variable List, and then clears the
         *multiselector and finishes the ActionMode. If the deletion is cancelled, the NegativeClick
         *callback simply clears the multiselector and finishes the ActionMode.
         */
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            //Fetch a List of the Runs by Adapter position that have been selected for deletion.
            mDeleteList = mMultiSelector.selectedPositions
            mActionMode = mode
            /*Now invoke the deletion confirmation dialog, telling where the request comes from and
             *passing along the number of Runs to be deleted so the dialog's message will be
             *accurate.
             */
            val args = Bundle()
            args.putInt(Constants.FRAGMENT, Constants.RUN_LIST_RECYCLER_FRAGMENT)
            args.putInt(Constants.NUMBER_OF_RUNS, mDeleteList!!.size)
            val dialog = DeleteRunsDialog()
            dialog.arguments = args
            dialog.show(activity!!.supportFragmentManager, "DeleteDialog")
            return true
        }
    }

    private fun deleteRuns() {
        /*If a Run to be deleted is being tracked, stop tracking - otherwise location updates will
         *continue with updates not associated with any Run and really with no way to turn updates
         *off. First, check to see if we're tracking any Run.
         */
        if (RunManager.isTrackingRun) {
            /*We know a Run's being tracked; check the Runs selected for deletion to see if
             *it's included; if so, stop location updates before deleting.
             */
            for (i in mDeleteList!!.indices.reversed()) {
                if (RunManager.isTrackingRun(RunManager.getRun(mAdapter!!.getItemId(mDeleteList!![i])))) {
                    mService!!.stopLocationUpdates()
                }
            }
        }
        /*Take the list of adapter positions selected for deletion from the highest position to the
         *lowest and add the RunId for the Run displayed in each position to a list of RunIds to use
         *for the actual deletion process. The adapter positions have to be taken from the highest down
         *so that earlier deletions will not change the adapter positions of later Runs to be deleted.
         */
        val runsToDelete = ArrayList<Long>()
        for (i in mDeleteList!!.indices.reversed()) {
            runsToDelete.add(mAdapter!!.getItemId(mDeleteList!![i]))
        }
        Log.i(TAG, "runsToDelete is: " + runsToDelete.toString())
        RunManager.deleteRuns(runsToDelete)
        /*Clean up the MultiSelector, finish the ActionMode, and refresh the UI now that our
         *dataset has changed.
         */
        mMultiSelector.clearSelections()
        mActionMode!!.finish()
        refreshUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        if (savedInstanceState != null) {
            /*Get sort order and subtitle from savedInstanceState Bundle if the Activity is
             *getting recreated.
             */
            mSortOrder = savedInstanceState.getInt(Constants.SORT_ORDER)

            (activity as AppCompatActivity).supportActionBar!!.setSubtitle(savedInstanceState
                    .getString(Constants.SUBTITLE))
        } else {
            /*When Activity is created for the first time or if the Fragment is getting created
             *for the first time even though the Activity isn't, get sort order and subtitle
             *from SharedPreferences.
             */
            mSortOrder = RunTracker2Kotlin.prefs!!.getInt(Constants.SORT_ORDER,
                    Constants.SORT_BY_DATE_DESC)
            mSubtitle = RunTracker2Kotlin.prefs!!.getString(Constants.SUBTITLE, "Stub Value")
        }
        //Define the kinds of Intents we want to know about and set up BroadcastReceiver accordingly.
        mIntentFilter = IntentFilter(Constants.ACTION_DELETE_RUNS)
        mIntentFilter!!.addAction(Constants.SEND_RESULT_ACTION)
        mResultsReceiver = ResultsReceiver()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val args = Bundle()
        args.putInt(Constants.SORT_ORDER, mSortOrder)
        loaderManager.initLoader(Constants.RUN_LIST_LOADER, args, this)
    }

    override fun onStart() {
        super.onStart()
        val bindIntent = Intent(context, BackgroundLocationService::class.java)
        context!!.bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    //Function to set subtitle according to the number of Runs recorded and their sort order..
    private fun setSubtitle() {
        val r = activity!!.resources
        val subtitle: String
        if (mAdapter!!.itemCount == 0) {
            subtitle = r.getString(R.string.no_runs_recorded)
        } else {
            when (mSortOrder) {
                Constants.SORT_BY_DATE_ASC      -> subtitle = r.getQuantityString(R.plurals.recycler_subtitle_date_asc,
                        mAdapter!!.itemCount, mAdapter!!.itemCount)
                Constants.SORT_BY_DATE_DESC     -> subtitle = r.getQuantityString(R.plurals.recycler_subtitle_date_desc,
                        mAdapter!!.itemCount, mAdapter!!.itemCount)
                Constants.SORT_BY_DISTANCE_ASC  -> subtitle = r.getQuantityString(R.plurals.recycler_subtitle_distance_asc,
                        mAdapter!!.itemCount, mAdapter!!.itemCount)
                Constants.SORT_BY_DISTANCE_DESC -> subtitle = r.getQuantityString(R.plurals.recycler_subtitle_distance_desc,
                        mAdapter!!.itemCount, mAdapter!!.itemCount)
                Constants.SORT_BY_DURATION_ASC  -> subtitle = r.getQuantityString(R.plurals.recycler_subtitle_duration_asc,
                        mAdapter!!.itemCount, mAdapter!!.itemCount)
                Constants.SORT_BY_DURATION_DESC -> subtitle = r.getQuantityString(R.plurals.recycler_subtitle_duration_desc,
                        mAdapter!!.itemCount, mAdapter!!.itemCount)
                else                            -> subtitle = r.getString(R.string.goof_up)
            }
        }

        (activity as AppCompatActivity).supportActionBar!!.setSubtitle(subtitle)
    }

    override fun onSaveInstanceState(saveInstanceState: Bundle) {
        super.onSaveInstanceState(saveInstanceState)
        //We need to save the sort order for the runs when configurations change.
        saveInstanceState.putInt(Constants.SORT_ORDER, mSortOrder)
        RunTracker2Kotlin.prefs!!.edit().putInt(Constants.SORT_ORDER, mSortOrder).apply()

        saveInstanceState.putString(Constants.SUBTITLE,
                (activity as AppCompatActivity).supportActionBar!!.subtitle!!.toString())
        try {
            //noinspection ConstantConditions,ConstantConditions
            RunTracker2Kotlin.prefs!!.edit().putString(Constants.SUBTITLE,
                    (activity as AppCompatActivity).supportActionBar!!.subtitle!!
                            .toString()).apply()
        } catch (npe: NullPointerException) {
            Log.i(TAG, "Couldn't write subtitle to default preferences file - attempt to get " + "SupportActionBar returned a null pointer.")
        }

        saveInstanceState.putInt(Constants.ADAPTER_ITEM_COUNT, mAdapter!!.itemCount)
        RunTracker2Kotlin.prefs!!.edit().putInt(Constants.ADAPTER_ITEM_COUNT,
                mAdapter!!.itemCount).apply()
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, parent, savedInstanceState)

        val v = inflater.inflate(R.layout.fragment_recycler_run_list, parent, false)
        mRunListRecyclerView = v.findViewById(R.id.run_recycler_view)
        mRunListRecyclerView!!.layoutManager = LinearLayoutManager(activity)
        mRunListRecyclerView!!.addItemDecoration(SimpleDividerItemDecoration(getContext()!!))
        val itemAnimator = DefaultItemAnimator()
        itemAnimator.removeDuration = 1000
        itemAnimator.addDuration = 1000
        mRunListRecyclerView!!.itemAnimator = itemAnimator
        @SuppressLint("Recycle") val cursor = context!!.contentResolver.query(
                Constants.URI_TABLE_RUN, null, null, null,
                Constants.SORT_NO_RUNS.toString()
        )
        mAdapter = RunRecyclerListAdapter(getContext()!!, cursor)
        mAdapter!!.setHasStableIds(true)
        mRunListRecyclerView!!.adapter = mAdapter
        //Set up UI elements to display if there are no Runs recorded to display in the RecyclerView.
        mEmptyViewTextView = v.findViewById(R.id.empty_view_textview)
        mEmptyViewButton = v.findViewById(R.id.empty_view_button)
        mEmptyViewButton!!.setOnClickListener { v1 -> RunManager.insertRun() }
        /*Flag first visit so we don't check for which Run we were on when we pressed the Back button
         *in RunPagerAdapter.
         */
        mFirstVisit = true
        refreshUI()
        return v
    }

    private fun refreshUI() {
    /*Restarting the loader forces an update of the UI: notifyDataSetChanged() gets called on the
     *adapter, which in turn causes onBindHolder() to get called, thus updating the RecyclerView's
     *contents and the background of any selected RunHolder item. When a new sort order is selected,
     *we invoke restartLoader() to get a Loader with fresh data.
     */

    /*If we have no Runs recorded, hide the RecyclerView and display a TextView and Button
     *inviting the user to record the first Run.
     */
        if (mAdapter!!.itemCount == 0) {
            mRunListRecyclerView!!.visibility = View.GONE
            mEmptyViewTextView!!.visibility = View.VISIBLE
            mEmptyViewButton!!.visibility = View.VISIBLE
        } else {
            mEmptyViewTextView!!.visibility = View.GONE
            mEmptyViewButton!!.visibility = View.GONE
            mRunListRecyclerView!!.visibility = View.VISIBLE
        }
        /*Disable the New Run Menu item if we're tracking a run - trying to start a new run while
         *another's already being tracked will crash the program!
         */
        if (mOptionsMenu != null) {

            mOptionsMenu!!.findItem(R.id.menu_item_new_run).isEnabled = !(RunManager.isTrackingRun)

            //Disable the Sort Runs menu if there're fewer than two Runs - nothing to sort!
            mOptionsMenu!!.findItem(R.id.menu_item_sort_runs).isEnabled = !(mAdapter!!.itemCount < 2)

        }
        setSubtitle()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater!!.inflate(R.menu.run_list_options, menu)
        mOptionsMenu = menu
    }

    override fun onPrepareOptionsMenu(menu: Menu?) {
        /*Set title of menuItem to change distance measurement units according
         *to the units currently used.
         */
        val item = mOptionsMenu!!.findItem(R.id.recycler_menu_item_units)
        if (RunTracker2Kotlin.prefs!!.getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)) {
            item.setTitle(R.string.imperial)
        } else {
            item.setTitle(R.string.metric)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {

        when (item!!.itemId) {
            R.id.recycler_menu_item_units        -> {
                /*Changed shared prefs value for measurement system and force adapter to redraw all
                 *items in the RecyclerView.
                 */
                RunTracker2Kotlin.prefs!!.edit().putBoolean(Constants.MEASUREMENT_SYSTEM,
                        !RunTracker2Kotlin.prefs!!.getBoolean(Constants.MEASUREMENT_SYSTEM,
                                Constants.IMPERIAL)).apply()
                mAdapter!!.notifyDataSetChanged()
                activity!!.invalidateOptionsMenu()
                return true
            }
            R.id.menu_item_new_run               -> {
                RunManager.insertRun()
                return true
            }
        /*Change the sort order of the RecyclerView and the Activity subtitle to match based
             *upon the menuItem selected.
             */
            R.id.menu_item_sort_by_date_asc      -> {
                changeSortOrder(Constants.SORT_BY_DATE_ASC)
                return true
            }
            R.id.menu_item_sort_by_date_desc     -> {
                changeSortOrder(Constants.SORT_BY_DATE_DESC)
                return true
            }
            R.id.menu_item_sort_by_distance_asc  -> {
                changeSortOrder(Constants.SORT_BY_DISTANCE_ASC)
                return true
            }
            R.id.menu_item_sort_by_distance_desc -> {
                changeSortOrder(Constants.SORT_BY_DISTANCE_DESC)
                return true
            }
            R.id.menu_item_sort_by_duration_asc  -> {
                changeSortOrder(Constants.SORT_BY_DURATION_ASC)
                return true
            }
            R.id.menu_item_sort_by_duration_desc -> {
                changeSortOrder(Constants.SORT_BY_DURATION_DESC)
                return true
            }
            else                                 -> return super.onOptionsItemSelected(item)
        }
    }

    /*Whenever we change the sort order, a new query must be made on the database, which
     *in turn requires creation of a new RUN_LIST_LOADER, so we need to call
     *restartLoader() to provide the RecyclerView's adapter with a new cursor of data
     *reflecting the new sort order. We also change the ActionBar's subtitle to display
     *the new sort order.
     */
    private fun changeSortOrder(sortOrder: Int) {
        val args = Bundle()
        mSortOrder = sortOrder
        RunTracker2Kotlin.prefs!!.edit().putInt(Constants.SORT_ORDER, sortOrder).apply()
        setSubtitle()
        RunTracker2Kotlin.prefs!!.edit().putString(Constants.SUBTITLE, mSubtitle).apply()
        args.putInt(Constants.SORT_ORDER, sortOrder)
        loaderManager.restartLoader(Constants.RUN_LIST_LOADER, args, this)
        setSubtitle()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(activity!!).registerReceiver(
                mResultsReceiver!!,
                mIntentFilter!!)

        /*Get the sort order from SharedPreferences here in case we're resumed from the
         *RunPagerActivity without being recreated.  That way, the sort order will be what it was in
         *the RunPagerActivity.
         */
        mSortOrder = RunTracker2Kotlin.prefs!!.getInt(Constants.SORT_ORDER, Constants.SORT_BY_DATE_DESC)
        mSubtitle = RunTracker2Kotlin.prefs!!.getString(Constants.SUBTITLE,
                "Stub Value")
        changeSortOrder(mSortOrder)
        refreshUI()
        /*If we're coming back here from the RunPagerActivity, check which Run was displayed there
         *and scroll the RecyclerList to place that Run at the top of the display.
         */
        if (!mFirstVisit) {
            /*First fetch the position the displayed Run had in the RunPager - all positions in
             *the RunPager map directly to positions in the adapter and the RecyclerView.
             */
            val adapterPosition = RunTracker2Kotlin.prefs!!.getInt(Constants.ADAPTER_POSITION, 0)
            val lm = mRunListRecyclerView!!.layoutManager as LinearLayoutManager
            /*Scroll RecyclerView so the designated Run is displayed 20 pixels below the top of the
             *display.
             */
            lm.scrollToPositionWithOffset(adapterPosition, 20)
        }
        //We will now have displayed the RecyclerView at least once, so clear the FirstVisit flag.
        mFirstVisit = false
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(activity!!).unregisterReceiver(mResultsReceiver!!)
        super.onPause()
    }

    override fun onStop() {
        if (mBound) {
            context!!.unbindService(mServiceConnection)
        }
        super.onStop()
    }

    fun onDeleteRunsDialogPositiveClick() {
        deleteRuns()
    }

    fun onDeleteRunsDialogNegativeClick() {
        /*If we don't select Delete from the ActionMode menu, just clear the MultiSelector and the
         *ActionMode without doing anything.
         */
        mMultiSelector.clearSelections()
        mActionMode!!.finish()
        refreshUI()
    }

    override fun onCreateLoader(d: Int, args: Bundle?): Loader<Cursor> {
        /*We only ever load the list of all Runs in this Fragment, so assume here that this is the
         *case. We need to pass along a reference to the Uri on the table in question to allow
         *observation for content changes used in auto-updating by the loader. We also need to
         *extract the sort order for the loader from the Bundle args. Args might be null upon
         *initial start of the program, so check for it; the default value of SORT_DATE_DESC is set
         *in the initialization of RunRecyclerListFragment member variable mSortOrder.
         */

        if (args != null)
        /*If args is null, the default value of mSortOrder set at the beginning of this
             *fragment will apply.
             */
            mSortOrder = args.getInt(Constants.SORT_ORDER)

        return RunListCursorLoader(context!!, mSortOrder)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor) {
        /*Now that we've got a fresh cursor of data, swap out from the adapter the old cursor for the
        *new one and notify the adapter that its data has changed so it will refresh the
        *RecyclerView display. An alternative approach is simply to create a new adapter here, load
        *the cursor into it, and attach the new adapter to the RecyclerListView. The loader should
        *take care of closing the old cursor, so use swapCursor(), not changeCursor().
        */
        mAdapter!!.swapCursor(cursor)
        mAdapter!!.notifyDataSetChanged()
        refreshUI()
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        //Stop using the cursor (via the adapter)
        mRunListRecyclerView!!.adapter = null
    }

    /*ViewHolder class for use with RecyclerListView. Big Nerd Ranch's SwappingHolder swaps state
     *depending upon whether the ViewHolder has been selected.
     */
    inner class RunHolder//Pass the itemView to SwappingHolder constructor to get its features in our ViewHolder class.
    internal constructor(itemView: View) : SwappingHolder(itemView, mMultiSelector), View.OnClickListener, View.OnLongClickListener {
        internal var mRun: Run? = null
        internal val mRunNumberTextView: TextView
        internal val mStartDateTextView: TextView
        internal val mStartAddressTextView: TextView
        internal val mDistanceTextView: TextView
        internal val mDurationTextView: TextView
        internal val mEndAddressTextView: TextView

        init {
            itemView.setOnClickListener(this)
            itemView.isLongClickable = true
            itemView.setOnLongClickListener(this)

            mRunNumberTextView = itemView.findViewById(R.id.run_number_textview)
            mStartDateTextView = itemView.findViewById(R.id.list_item_date_textview)
            mStartAddressTextView = itemView.findViewById(R.id.list_item_start_address_textview)
            mDistanceTextView = itemView.findViewById(R.id.list_item_distance_textview)
            mDurationTextView = itemView.findViewById(R.id.list_item_duration_textview)
            mEndAddressTextView = itemView.findViewById(R.id.list_item_end_address_textview)
        }

        //Plug values from a specific Run into the View elements of the RunHolder.
        internal fun bindRun(run: Run?) {
            mRun = run
            val r = activity!!.resources
            mRunNumberTextView.text = r.getString(R.string.run_list_run_number, this.adapterPosition + 1,
                    mAdapter!!.getItemId(layoutPosition).toInt())
            val startDateText = Constants.DATE_FORMAT.format(mRun!!.startDate)
            mStartDateTextView.text = startDateText
            mStartAddressTextView.text = mRun!!.startAddress
            mDistanceTextView.text = r.getString(R.string.list_distance_text, RunManager.formatDistance(mRun!!.distance))
            val durationText = Run.formatDuration(mRun!!.duration.toInt() / 1000)
            mDurationTextView.text = r.getString(R.string.list_duration_text, durationText)
            mEndAddressTextView.text = mRun!!.endAddress
        }

        override fun onClick(v: View) {
            if (mRun == null) {
                return
            }
            /*If this RunHolder hasn't been selected for deletion in an ActionMode, start
             *RunPagerActivity specifying its mRun as the one to be displayed when the ViewPager
             *first opens.
             */
            if (!mMultiSelector.tapSelection(this)) {
                val i = RunPagerActivity.newIntent(getContext()!!,
                        this@RunRecyclerListFragment.mSortOrder,
                        mRun!!.id)
                RunTracker2Kotlin.prefs!!.edit().putLong(Constants.PREF_CURRENT_RUN_ID,
                        mRun!!.id).apply()
                RunTracker2Kotlin.prefs!!.edit().putFloat(Constants.ZOOM_LEVEL, 17.0f).apply()
                startActivity(i)
            }
        }

        override fun onLongClick(v: View): Boolean {
            //On a long click, start an ActionMode and mark this RunHolder as selected.
            (activity as AppCompatActivity).startSupportActionMode(mDeleteMode)
            mMultiSelector.setSelected(this, true)
            return true
        }

    }

    //Custom adapter to feed the RecyclerListView RunHolders filled with data from the correct Runs.
    inner class RunRecyclerListAdapter internal constructor(context: Context, cursor: Cursor) : CursorRecyclerViewAdapter<RunHolder>(context, cursor) {

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunHolder {
            val itemView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.list_item_layout, parent, false)
            return RunHolder(itemView)
        }

        override fun onBindViewHolder(holder: RunHolder, cursor: Cursor) {
            val run = RunDatabaseHelper.getRun(cursor)
            //Change the background of this RunHolder if its Run is being tracked
            if (RunManager.isTrackingRun(run)) {
                holder.itemView.setBackgroundResource(R.drawable.selected_backgound_activated)
            } else {
                holder.itemView.setBackgroundResource(R.drawable.background_activated)
            }
            holder.bindRun(run)
        }
    }

    //Broadcast Receiver to receiver reports of results of operations this Fragment is interested in.
    private inner class ResultsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action != null && action == Constants.ACTION_DELETE_RUNS) {
                /*The Runnable deletion tasks broadcast results of their operations method in
                 *Intents so they can be displayed to the user.
                 */
                val resultsString = intent.getStringExtra(Constants.EXTENDED_RESULTS_DATA)
                /*This HashMap keeps track of which of the Runs requested to be deleted were
                *actually successfully deleted so  that the adapter can be notified of the items
                *that were deleted. A LinkedHashMap is needed so that the notifications will be
                *in the correct order, highest numbered view to lowest.
                */
                val wasRunDeleted = intent
                        .getSerializableExtra(Constants.EXTRA_VIEW_HASHMAP) as LinkedHashMap<Long, Boolean>
                val keys = wasRunDeleted.keys
                for (k in keys) {
                    if (wasRunDeleted[k]!!) {
                        val l = k.toInt()
                        mAdapter!!.notifyItemRemoved(l)
                        mAdapter!!.notifyItemRangeChanged(l, mAdapter!!.itemCount)
                        Log.i(TAG, "Notified RunList Adapter that Run " + l.toString() + " was deleted")
                    } else {
                        Log.d(TAG, "Run " + k + " was not deleted, so not notifying the adapter "
                                + "it was deleted")
                    }
                }
                //Now give the user a summary of the results of the deletion operation

                val builder = AlertDialog.Builder(getContext()!!)
                        .setPositiveButton("OK") { dialogInterface, i -> dialogInterface.dismiss() }
                        .setTitle("Run Deletion Report")
                        .setMessage(resultsString)
                builder.create().show()
                refreshUI()
            } else if (action != null && action == Constants.SEND_RESULT_ACTION) {
                val actionAttempted = intent
                        .getStringExtra(Constants.ACTION_ATTEMPTED)
                when (actionAttempted) {
                    Constants.ACTION_INSERT_RUN         -> {
                        /*Now that the Runnable task has gotten the new Run inserted into the Run
                         *table of the database, we have a RunId assigned to it that can be used to
                         *start the RunPagerActivity with the new Run's CombinedFragment as the
                         *current item in the ViewPager.
                         */
                        val run = intent.getParcelableExtra<Run>(Constants.EXTENDED_RESULTS_DATA)
                        val runId = run.id
                        if (runId != -1L) {
                            //Update the Subtitle to reflect the new number of Runs
                            setSubtitle()
                            //Start the RunPagerActivity to display the newly created Run
                            val i = RunPagerActivity.newIntent(getContext()!!, mSortOrder, runId)
                            startActivity(i)
                        } else {
                            Toast.makeText(activity,
                                    R.string.insert_run_error,
                                    Toast.LENGTH_LONG)
                                    .show()
                        }
                    }
                    Constants.ACTION_INSERT_LOCATION    -> {
                        //Results of location insertions are reported only if there's an error.
                        val resultString = intent.getStringExtra(Constants.EXTENDED_RESULTS_DATA)
                        Toast.makeText(getContext(), resultString, Toast.LENGTH_LONG).show()
                    }
                    Constants.ACTION_UPDATE_END_ADDRESS -> {
                        val results = intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1)
                        /*Successful updates are not reported by the Runnable task, so no need to
                         *check for them.
                         */
                        if (results > 1) {
                            Toast.makeText(activity,
                                    R.string.multiple_runs_end_addresses_updated,
                                    Toast.LENGTH_LONG)
                                    .show()
                        } else if (results == 0) {
                            Toast.makeText(activity,
                                    R.string.update_end_address_failed,
                                    Toast.LENGTH_LONG)
                                    .show()
                        } else if (results != 1) {
                            Toast.makeText(activity,
                                    R.string.unknown_end_address_update_error,
                                    Toast.LENGTH_LONG)
                                    .show()
                        }
                    }
                }
            }
        }
    }

    companion object {

        private val TAG = "RunRecyclerListFragment"
    }
}
