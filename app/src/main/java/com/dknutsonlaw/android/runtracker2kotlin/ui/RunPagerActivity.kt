package com.dknutsonlaw.android.runtracker2kotlin.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
//import android.content.ComponentName;
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.support.v4.app.FragmentManager
import android.support.v4.app.LoaderManager
import android.support.v4.content.Loader
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.view.ViewPager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.dknutsonlaw.android.runtracker2kotlin.BackgroundLocationService
import com.dknutsonlaw.android.runtracker2kotlin.Constants
import com.dknutsonlaw.android.runtracker2kotlin.R
import com.dknutsonlaw.android.runtracker2kotlin.Run
import com.dknutsonlaw.android.runtracker2kotlin.RunDatabaseHelper
import com.dknutsonlaw.android.runtracker2kotlin.RunListCursorLoader
import com.dknutsonlaw.android.runtracker2kotlin.RunManager
import com.dknutsonlaw.android.runtracker2kotlin.RunTracker2Kotlin

/**
 * Created by dck on 9/7/15. Display all the Runs we've recorded in a ViewPager that displays a
 * CombinedFragment in each of its pages.
 */
class RunPagerActivity : AppCompatActivity(), LoaderManager.LoaderCallbacks<Cursor>, DeleteRunsDialog.DeleteRunsDialogListener {

    private var mViewPager: ViewPager? = null
    private var mService: BackgroundLocationService? = null
    private var mBound = false
    private var mResultsReceiver: ResultsReceiver? = null
    private var mIntentFilter: IntentFilter? = null
    private var mMenu: Menu? = null
    //Custom Adapter to feed CombinedFragments to the ViewPager.
    private var mAdapter: RunCursorFragmentStatePagerAdapter? = null
    private var mRunId: Long = -1
    //Set a default sort order
    private var mSortOrder = Constants.SORT_BY_DATE_DESC

    private val mPageChangeListener = object : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            /*Keep the currently displayed Run's position in the ViewPager and Adapter so the
         *RecyclerView can scroll that Run to the top of its display when we go back there.
         */
            RunTracker2Kotlin.prefs!!.edit().putInt(Constants.ADAPTER_POSITION, position).apply()
            /*Make sure that mRunId is always equal to the run id of the currently viewed
         *CombinedFragment as we page through them in the ViewPager.
         */
            val fragment = mAdapter!!.getItem(position) as CombinedFragment
            mRunId = fragment.arguments!!.getLong(Constants.ARG_RUN_ID, -1)
            RunTracker2Kotlin.prefs!!.edit().putLong(Constants.ARG_RUN_ID, mRunId).apply()
            RunTracker2Kotlin.prefs!!.edit().putLong(Constants.CURRENTLY_VIEWED_RUN, mRunId).apply()
            /*Update the subtitle to show position of this Run in the current sort of Runs and the
         *total number of Runs in the ViewPager.
         */
            setSubtitle()
            invalidateFragmentMenus(position)
        }

    }

    private val mAdapterChangeListener = ViewPager.OnAdapterChangeListener { viewPager, oldAdapter, newAdapter ->
        /*A new Adapter means a new SortOrder, so we need to update the Run's position in the
             *Adapter and ViewPager so that the RecyclerView can scroll to it when we go back there.
             */
        RunTracker2Kotlin.prefs!!.edit().putInt(Constants.ADAPTER_POSITION,
                mViewPager!!.currentItem).apply()
        /*The order of the fragments in the adapter and ViewPager has changed, so the suppression
             *of menus for non-visible fragments has to be redone.
             */
        invalidateFragmentMenus(mViewPager!!.currentItem)
    }

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

    /*Disable menu items added by CombinedFragments for CombinedFragments that aren't currently
     *displayed to avoid duplication of those menu items - mViewPager initializes up to three
     *CombinedFragments at one time and all their menu items will be displayed simultaneously
     *if the ones for fragments no visible aren't suppressed.
     */
    private fun invalidateFragmentMenus(position: Int) {
        for (i in 0 until mAdapter!!.count) {
            val fragment = mAdapter!!.getItem(i) as CombinedFragment
            fragment.setHasOptionsMenu(i == position)
        }
        //We've changed the menu, so invalidate it to get it redisplayed with the new configuration.
        invalidateOptionsMenu()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_run_pager)
        /*The sort order and the Id of the CombinedFragment to make the current view in the
         *ViewPager are the critical initialization variables, so either retrieve them from the
         *Intent that started this Activity or retrieve them from the savedInstanceState Bundle.
         */
        if (savedInstanceState != null) {
            mSortOrder = savedInstanceState.getInt(Constants.SORT_ORDER)
            mRunId = savedInstanceState.getLong(Constants.SAVED_RUN_ID)
        } else {
            mSortOrder = intent.getIntExtra(Constants.EXTRA_SORT_ORDER,
                    Constants.KEEP_EXISTING_SORT)
            //If we're keeping he same sort order, get it from SharedPrefs.
            if (mSortOrder == Constants.KEEP_EXISTING_SORT) {
                mSortOrder = RunTracker2Kotlin.prefs!!.getInt(Constants.SORT_ORDER,
                        Constants.SORT_BY_DATE_DESC)
            }
            mRunId = intent.getLongExtra(Constants.EXTRA_RUN_ID, -1)
        }
        /*Make the relevant run ID an Argument to the CombinedFragment to be opened so it will be
         *easily available there.
         */
        RunTracker2Kotlin.prefs!!.edit().putLong(Constants.ARG_RUN_ID, mRunId).apply()
        /*Default zoom value for CombinedFragments' maps is 17.0f, but higher zoom levels are
         *preserved.
         */
        val zoom = if (RunTracker2Kotlin.prefs!!.getFloat(Constants.ZOOM_LEVEL, 17.0f) > 17.0f)
            RunTracker2Kotlin.prefs!!.getFloat(Constants.ZOOM_LEVEL, 17.0f)
        else
            17.0f
        RunTracker2Kotlin.prefs!!.edit().putFloat(Constants.ZOOM_LEVEL, zoom).apply()
        mViewPager = findViewById(R.id.activity_run_pager_view_pager)
        //Set up BroadcastReceiver to receive results of operations we're interested in.
        mIntentFilter = IntentFilter(Constants.SEND_RESULT_ACTION)
        mIntentFilter!!.addAction(Constants.ACTION_DELETE_RUN)
        mResultsReceiver = ResultsReceiver()
        val args = setupAdapterAndLoader()
        supportLoaderManager.initLoader(Constants.RUN_LIST_LOADER, args, this)
    }

    public override fun onStart() {
        super.onStart()
        val bindIntent = Intent(this, BackgroundLocationService::class.java)
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    @SuppressLint("Recycle")
    private fun setupAdapterAndLoader(): Bundle {
        /*Set up the Adapter and  Loader by constructing the initial data cursor based upon
         *the selected sort order.
         */

        val args = Bundle()
        var cursor: Cursor? = null

        when (mSortOrder) {
            Constants.SORT_BY_DATE_ASC      -> cursor = contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DATE_ASC.toString()
            )
            Constants.SORT_BY_DATE_DESC     -> cursor = contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DATE_DESC.toString()
            )
            Constants.SORT_BY_DISTANCE_ASC  -> cursor = contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DISTANCE_ASC.toString()
            )
            Constants.SORT_BY_DISTANCE_DESC -> cursor = contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DISTANCE_DESC.toString()
            )
            Constants.SORT_BY_DURATION_ASC  -> cursor = contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DURATION_ASC.toString()
            )
            Constants.SORT_BY_DURATION_DESC -> cursor = contentResolver.query(
                    Constants.URI_TABLE_RUN, null, null, null,
                    Constants.SORT_BY_DURATION_DESC.toString()
            )
            Constants.SORT_NO_RUNS          -> {
                cursor = contentResolver.query(
                        Constants.URI_TABLE_RUN, null, null, null, null
                )
                Log.i(TAG, "Invalid sort order - how'd you get here!?!")
            }
            else                            -> Log.i(TAG, "Invalid sort order - how'd you get here!?!")
        }
        mAdapter = RunCursorFragmentStatePagerAdapter(this,
                supportFragmentManager,
                cursor!!)
        mViewPager!!.adapter = mAdapter
        //Make sure the ViewPager makes the designated Run's CombinedRunFragment the current view.
        setViewPager(mAdapter!!.cursor, mRunId)
        //Change the Activity's subtitle to display sort and number of Runs.
        setSubtitle()
        /*If there aren't any Runs left to display, close this Activity and go back to the
         *RunRecyclerListActivity and Fragment, which displays a message to the user.
         */
        if (mAdapter!!.count == 0) {
            finish()
        }
        args.putInt(Constants.SORT_ORDER, mSortOrder)
        return args
    }

    /*Setting the subtitle is broken out into a separate method from setting up the adapter
    * and loader because we need to change the subtitle whenever the Run displayed is changed and
    * we don't want to construct a new adapter and a new loader every time we do that.*/
    private fun setSubtitle() {
        val r = resources
        val subtitle: String?
        subtitle = when (mSortOrder) {
            Constants.SORT_BY_DATE_ASC      -> r.getQuantityString(R.plurals.viewpager_subtitle_date_asc,
                    mAdapter!!.count, mViewPager!!.currentItem + 1, mAdapter!!.count)
            Constants.SORT_BY_DATE_DESC     -> r.getQuantityString(R.plurals.viewpager_subtitle_date_desc,
                    mAdapter!!.count, mViewPager!!.currentItem + 1, mAdapter!!.count)
            Constants.SORT_BY_DISTANCE_ASC  -> r.getQuantityString(R.plurals.viewpager_subtitle_distance_asc,
                    mAdapter!!.count, mViewPager!!.currentItem + 1, mAdapter!!.count)
            Constants.SORT_BY_DISTANCE_DESC -> r.getQuantityString(R.plurals.viewpager_subtitle_distance_desc,
                    mAdapter!!.count, mViewPager!!.currentItem + 1, mAdapter!!.count)
            Constants.SORT_BY_DURATION_ASC  -> r.getQuantityString(R.plurals.viewpager_subtitle_duration_asc,
                    mAdapter!!.count, mViewPager!!.currentItem + 1, mAdapter!!.count)
            Constants.SORT_BY_DURATION_DESC -> r.getQuantityString(R.plurals.viewpager_subtitle_duration_desc,
                    mAdapter!!.count, mViewPager!!.currentItem + 1, mAdapter!!.count)
            else                            -> r.getString(R.string.goof_up)
        }
        supportActionBar!!.subtitle = subtitle
    }

    public override fun onPause() {
        mViewPager!!.removeOnPageChangeListener(mPageChangeListener)
        mViewPager!!.removeOnAdapterChangeListener(mAdapterChangeListener)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mResultsReceiver!!)
        super.onPause()
    }

    public override fun onStop() {
        if (mBound) {
            unbindService(mServiceConnection)
        }
        super.onStop()
    }

    public override fun onResume() {
        super.onResume()
        mViewPager!!.addOnPageChangeListener(mPageChangeListener)
        mViewPager!!.addOnAdapterChangeListener(mAdapterChangeListener)
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mResultsReceiver!!,
                mIntentFilter!!)

    }

    /*@Override
    public void onRestart(){
        super.onRestart();
        *//*If the user gets to the RunPagerActivity by hitting the Back button in the RunMapPagerActivity,
         *we need to retrieve the RunId and Sort Order from SharedPrefs. This code has to go here, not
         *in onResume(), because we only want this behavior the happen when the Activity has already been
         *opened before with an Intent. When this Activity is opened for the first time, it gets its
         *values from the Intent dispatched by the RunRecyclerListFragment.
         *//*
        //Log.i(TAG, "Calling onRestart()");
        mRunId = RunTracker2Kotlin.getPrefs().getLong(Constants.ARG_RUN_ID, -1);
        //Log.i(TAG, "mRunId in onRestart() is " + mRunId);
        mSortOrder = RunTracker2Kotlin.getPrefs().getInt(Constants.SORT_ORDER, Constants.KEEP_EXISTING_SORT);
        //Log.i(TAG, "mSortOrder in onRestart() is " + mSortOrder);
        Bundle args = setupAdapterAndLoader();
        getSupportLoaderManager().restartLoader(Constants.RUN_LIST_LOADER, args, this);
    }*/

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.run_pager_options, menu)
        mMenu = menu
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {

        super.onPrepareOptionsMenu(menu)

        //If we have fewer than two Runs, there's nothing to sort, so disable sort menu
        menu.findItem(R.id.run_map_pager_menu_item_sort_runs).isEnabled = mAdapter!!.count >= 2
        /*If we're tracking a Run, don't allow creation of a new Run - trying to track more than one
         *Run will crash the app!
         */
        menu.findItem(R.id.run_map_pager_menu_item_new_run).isEnabled = !RunManager.isTrackingRun
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.run_map_pager_menu_item_new_run               -> {
                /*Don't need to tell the Adapter its getting an update because we're recreating the
                 *Adapter shortly. First, tell the ViewPager's adapter that its content is receiving
                 *an update.
                 */
                mAdapter!!.startUpdate(mViewPager!!)
                //Now start a new blank run with nothing but a Start Date and a runId.
                RunManager.insertRun()
                /*The Adapter, Subtitle and Loader get reset when the results of the Insert Run
                 *action get reported to the ResultsReceiver in this Activity..
                 */
                return true
            }
            R.id.menu_item_map_pager_delete_run                -> {
                /*Bring up a confirmation dialog to allow the user to change his mind about deletion.
                 *We pass along this Activity's identity and that only a single Run is to be deleted
                 *so the dialog message will be accurate.
                 */
                val bundle = Bundle()
                bundle.putInt(Constants.FRAGMENT, Constants.COMBINED_FRAGMENT)
                bundle.putInt(Constants.NUMBER_OF_RUNS, 1)
                val dialog = DeleteRunsDialog()
                dialog.arguments = bundle
                dialog.show(supportFragmentManager, "DeleteDialog")
                return true
            }
        /*To change the sort order, set mSortOrder, store it to SharedPrefs, reinitialize the
             *adapter and subtitle and restart the RunListLoader.
             */
            R.id.run_map_pager_menu_item_sort_by_date_asc      -> mSortOrder = Constants.SORT_BY_DATE_ASC
            R.id.run_map_pager_menu_item_sort_by_date_desc     -> mSortOrder = Constants.SORT_BY_DATE_DESC
            R.id.run_map_pager_menu_item_sort_by_distance_asc  -> mSortOrder = Constants.SORT_BY_DISTANCE_ASC
            R.id.run_map_pager_menu_item_sort_by_distance_desc -> mSortOrder = Constants.SORT_BY_DISTANCE_DESC
            R.id.run_map_pager_menu_item_sort_by_duration_asc  -> mSortOrder = Constants.SORT_BY_DURATION_ASC
            R.id.run_map_pager_menu_item_sort_by_duration_desc -> mSortOrder = Constants.SORT_BY_DURATION_DESC
            R.id.show_entire_route_menu_item                   ->
                //This is implemented in the CombinedFragment
                return false
            R.id.track_end_point_menu_item                     ->
                //This is implemented in the CombinedFragment
                return false
            R.id.track_start_point_menu_item                   ->
                //This is implemented in the CombinedFragment
                return false
            R.id.tracking_off_menu_item                        ->
                //This is implemented in the CombinedFragment
                return false
            R.id.run_map_pager_activity_units                  ->
                //This is implemented in the CombinedFragment
                return false
            R.id.run_map_pager_activity_scroll                 ->
                //This is implemented in the CombinedFragment
                return false
            else                                               -> {
                super.onOptionsItemSelected(item)
                return true
            }
        }
        RunTracker2Kotlin.prefs!!.edit().putInt(Constants.SORT_ORDER, mSortOrder).apply()
        val args: Bundle = setupAdapterAndLoader()
        supportLoaderManager.restartLoader(Constants.RUN_LIST_LOADER, args, this)
        return true
    }

    //Method that's called by onDeleteRunDialogPositiveClick callback confirming deletion.
    private fun deleteRun() {
        //First, stop location updates if the Run we're deleting is currently being tracked.
        if (RunManager.isTrackingRun(RunManager.getRun(mRunId))) {
            mService!!.stopLocationUpdates()
            //We've stopped tracking any Run, so enable the "New Run" menu item.
            mMenu!!.findItem(R.id.run_map_pager_menu_item_new_run).isEnabled = true
            invalidateOptionsMenu()
        }
        /*Now order the Run to be deleted. The Adapter, Subtitle and Loader will get reset
         *when the results of the Run deletion get reported to the ResultsReceiver. First
         *notify the adapter that its contents will be updated.
         */
        mAdapter!!.startUpdate(mViewPager!!)
        RunManager.deleteRun(mRunId)
    }

    override fun onDeleteRunsDialogPositiveClick(which: Int) {
        //Check to see if this call from the dialog is for us; if so, delete the Run
        if (which == Constants.COMBINED_FRAGMENT) {
            deleteRun()
        }
    }

    override fun onDeleteRunsDialogNegativeClick(which: Int) {
        /*We don't need to do anything to cancel the deletion, but the interface requires that
         *this method be implemented.
         */
    }

    override fun onCreateLoader(d: Int, args: Bundle?): Loader<Cursor> {
        //Construct a cursor loader according to the selected sort order
        mSortOrder = args?.getInt(Constants.SORT_ORDER) ?: Constants.SORT_BY_DATE_DESC
        return RunListCursorLoader(this, mSortOrder)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor) {
        //The loader takes care of releasing the old cursor, so call swapCursor(), not changeCursor()
        mAdapter!!.swapCursor(cursor)
        /*If there are no Runs in the cursor, shut down this Activity and go back to the
         *RunRecyclerListActivity/Fragment, which has a special UI for when the database has no runs.
         */
        if (mAdapter!!.count == 0) {
            finish()
        }
        //Make sure we keep looking at the Run we were on before the Loader updated.
        setViewPager(cursor, mRunId)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {

    }

    public override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState!!.putLong(Constants.SAVED_RUN_ID, mRunId)
        outState.putInt(Constants.SORT_ORDER, mSortOrder)
    }

    /*Set the ViewPager's current (displayed) item to the specified Run and save the Adapter and
     *ViewPager position of the Run so the RecyclerView can scroll to it when we go back there.
     */
    private fun setViewPager(cursor: Cursor?, runId: Long) {
        cursor!!.moveToFirst()
        /*Iterate over the Runs in the cursor until we find the one with an Id equal to the one we
         *specified in the runId parameter, then set the ViewPager's current item to that Run and
         *save the Adapter/ViewPager position.
         */
        while (!cursor.isAfterLast) {
            if (RunDatabaseHelper.getRun(cursor)!!.id == runId) {
                mViewPager!!.currentItem = cursor.position
                RunTracker2Kotlin.prefs!!.edit().putInt(Constants.ADAPTER_POSITION,
                        mViewPager!!.currentItem).apply()
                break
            }
            cursor.moveToNext()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        //This method processes results of startResolutionForResult when Location Settings are bad.
        if (requestCode == Constants.LOCATION_SETTINGS_CHECK) {

            if (resultCode == Activity.RESULT_OK) {
                RunTracker2Kotlin.locationSettingsState = true
                Toast.makeText(this, "Location Settings now enabled.",
                        Toast.LENGTH_LONG).show()
            } else {
                RunTracker2Kotlin.locationSettingsState = false
                Toast.makeText(this, "Location Settings were not enabled. " + "Cannot track Run.", Toast.LENGTH_LONG).show()
            }
        }
    }

    //Custom adapter to feed CombinedRunFragments to the ViewPager
    private inner class RunCursorFragmentStatePagerAdapter internal constructor(context: Context, fm: FragmentManager, cursor: Cursor) : CursorFragmentStatePagerAdapter(context, fm, cursor) {
        //Pull a Run from the supplied cursor and retrieve its CombinedFragment using its RunId.
        override fun getItem(context: Context, cursor: Cursor): Fragment? {
            val runId = RunDatabaseHelper.getRun(cursor)!!.id
            return if (runId != -1L) {
                CombinedFragment.newInstance(runId)
            } else {
                /*We should never get here - Runs are assigned a RunId as soon as they get created and
                 *before they get added to the ViewPager, but we have return something in an "else"
                 *block to keep the compiler happy.
                 */
                null
            }
        }
    }

    /*Class to allow us to receive reports of results of the operations the ViewPager is interested
     *in, ACTION_INSERT_RUN and ACTION_DELETE_RUN.
     */
    private inner class ResultsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val r = resources
            val action = intent.action
            when (action) {
                Constants.SEND_RESULT_ACTION -> {
                    val actionAttempted = intent.getStringExtra(Constants.ACTION_ATTEMPTED)
                    if (actionAttempted == Constants.ACTION_INSERT_RUN) {
                        val run = intent.getParcelableExtra<Run>(Constants.EXTENDED_RESULTS_DATA)
                        if (run.id != -1L) {
                            /*Now that the new Run has been added to the database, we need to reset
                             *the Adapter, Subtitle and Loader.
                             */
                            mRunId = run.id
                            setupAdapterAndLoader()
                        } else {
                            Toast.makeText(this@RunPagerActivity, R.string.insert_run_error,
                                    Toast.LENGTH_LONG).show()
                        }
                        /*Now that the new Run has been entered into the database and the adapter,
                         *the ViewPager can finish the update of its View.
                         */
                        mAdapter!!.finishUpdate(mViewPager!!)
                        setViewPager(mAdapter!!.cursor, mRunId)
                    }
                }
                Constants.ACTION_DELETE_RUN  -> {
                    Log.i(TAG, "In ResultsReceiver for ACTION_DELETE_RUN in RunPagerActivity")
                    //Display a dialog displaying the results of the deletion operation.
                    //String resultsString = intent.getStringExtra(Constants.EXTENDED_RESULTS_DATA);
                    val runId = intent.getLongExtra(Constants.PARAM_RUN, -2)
                    val runsDeleted = intent.getIntExtra(Constants.RUNS_DELETED, -2)
                    val locationsDeleted = intent.getIntExtra(Constants.LOCATIONS_DELETED, -2)
                    Log.d(TAG, "Now building resultsString")
                    val stringBuilder = StringBuilder()
                    when (runsDeleted) {
                        1 -> stringBuilder.append(r.getString(R.string.delete_run_success, runId))
                        -1 -> stringBuilder.append(r.getString(R.string.delete_run_error, runId))
                        0 -> stringBuilder.append(r.getString(R.string.delete_run_failure, runId))
                        else -> stringBuilder.append(r.getString(R.string.delete_run_unexpected_return, runId))
                    }
                    when {
                        locationsDeleted == -1 -> stringBuilder.append(r.getString(R.string.delete_locations_error, runId))
                        locationsDeleted >= 0 -> stringBuilder.append(r.getQuantityString(R.plurals.location_deletion_results,
                                locationsDeleted, locationsDeleted, runId))
                        else -> stringBuilder.append(r.getString(R.string.delete_locations_unexpected_return, runId))
                    }
                    val resultsString = stringBuilder.toString()
                    Log.d(TAG, "Now displaying dialog reporting results")
                    val builder = AlertDialog.Builder(this@RunPagerActivity)
                            .setPositiveButton("OK") { dialogInterface, _ -> dialogInterface.dismiss() }
                            .setTitle("Run Deletion Report")
                            .setMessage(resultsString)
                    builder.create().show()
                    /*Trying to delete the last Run from this Activity after having deleted other
                     *Runs results in a problem: mRunId remains set to the last previous Run that
                     *was deleted, so we get an error for trying to delete a Run that's already
                     *been deleted. Thus, we need some technique to set a valid RunId for the
                     *new current view after deleting a Run.
                     *
                     *We use the position in the ViewPager held by the Run we just deleted to select
                     *what RunId should be after the deletion. If the ViewPager held only one
                     *child view before the deletion, we know we just deleted the last Run so we
                     *can just finish this activity and go back to RunRecyclerView.
                     */
                    Log.d(TAG, "Now cleaning up the ViewPager")
                    if (mViewPager!!.childCount == 1) {
                        mAdapter!!.finishUpdate(mViewPager!!)
                        finish()
                        /*If there was more than one Run held in the ViewPager, set the ViewPager's
                     *current view item to the view that's in the next higher position in the
                     *ViewPager unless we were already at the highest position, in which case
                     *set the ViewPager's current view item to the view that's in the next lower
                     *position in the ViewPager.
                     */
                    } else {
                        val currentPosition = mViewPager!!.currentItem
                        /*Get the fragment associated with the child view we're going to move
                         *to and get its RunId from the arguments that were attached to the
                         *fragment when it was created. Is there a better way to do this? Why
                         *doesn't the onPageChangeListener correctly report the fragment displayed
                         *in the last remaining page of a ViewPager?
                         */
                        val index: Int
                        index = if (currentPosition < mViewPager!!.childCount - 1) {
                            currentPosition + 1
                        } else {
                            currentPosition - 1
                        }
                        mViewPager!!.currentItem = index
                        val fragment = mAdapter!!.getItem(index) as CombinedFragment
                        mRunId = fragment.arguments!!.getLong(Constants.ARG_RUN_ID)
                        mAdapter!!.notifyDataSetChanged()
                    }
                    /*Now that we've got a "legal" mRunId, we can fetch a new cursor, reconstruct
                     *the adapter, and set the subtitle accordingly.
                     */
                    setupAdapterAndLoader()
                    //We've finished with deletions, so the  View's update can be finished.
                    mAdapter!!.finishUpdate(mViewPager!!)
                }
                else                         ->
                    /*Shouldn't ever get here - intent filter limits us to SEND_RESULT_ACTION
                     *and ACTION_DELETE_RUN.
                     */
                    Log.i(TAG, "Intent Action wasn't SEND_RESULT_ACTION or ACTION_DELETE_RUN")
            }/*ViewPager isn't interested in any other ACTION_ATTEMPTED, so no "else" clauses
                     *specifying what to do with them needed.
                     */
        }
    }

    companion object {

        private val TAG = "run_pager_activity"
        /*Static method to invoke this Activity and cause it to make the designated CombinedFragment the
     *current view in the ViewPager.
     */
        fun newIntent(packageContext: Context, sortOrder: Int, runId: Long): Intent {
            val intent = Intent(packageContext, RunPagerActivity::class.java)
            intent.putExtra(Constants.EXTRA_SORT_ORDER, sortOrder)
            intent.putExtra(Constants.EXTRA_RUN_ID, runId)
            return intent
        }
    }
}
