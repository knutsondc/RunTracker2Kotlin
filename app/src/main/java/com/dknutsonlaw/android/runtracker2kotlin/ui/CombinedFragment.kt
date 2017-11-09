package com.dknutsonlaw.android.runtracker2kotlin.ui

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.Fragment
import android.support.v4.app.LoaderManager
import android.support.v4.content.ContextCompat
import android.support.v4.content.Loader
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.dknutsonlaw.android.runtracker2kotlin.BackgroundLocationService
import com.dknutsonlaw.android.runtracker2kotlin.Constants
import com.dknutsonlaw.android.runtracker2kotlin.MyLocationListCursorLoader
import com.dknutsonlaw.android.runtracker2kotlin.R
import com.dknutsonlaw.android.runtracker2kotlin.Run
import com.dknutsonlaw.android.runtracker2kotlin.RunCursorLoader
import com.dknutsonlaw.android.runtracker2kotlin.RunDatabaseHelper
import com.dknutsonlaw.android.runtracker2kotlin.RunDatabaseHelper.Companion.getLocation
import com.dknutsonlaw.android.runtracker2kotlin.RunManager
import com.dknutsonlaw.android.runtracker2kotlin.RunTracker2Kotlin

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

import java.util.ArrayList
import java.util.Date

import com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener.REASON_API_ANIMATION

/**
 * Created by dck on 11/19/16. All the elements of the earlier RunFragment and RunMapFragment for a
 * given Run combined into a single Fragment
 */

class CombinedFragment : Fragment() {

    private var mRunManager: RunManager? = null
    private var mRun: Run? = null
    private var mRunId: Long = 0
    private var mStartLocation: Location? = null
    private var mPreviousLocation: Location? = null
    private var mLastLocation: Location? = null
    private var mStartButton: Button? = null
    private var mStopButton: Button? = null
    private var mStartedTextView: TextView? = null
    private var mStartingPointTextView: TextView? = null
    private var mStartingAltitudeTextView: TextView? = null
    private var mStartingAddressTextView: TextView? = null
    private var mEndedTextView: TextView? = null
    private var mEndingPointTextView: TextView? = null
    private var mEndingAltitudeTextView: TextView? = null
    private var mEndingAddressTextView: TextView? = null
    private var mDurationTextView: TextView? = null
    private var mDistanceCoveredTextView: TextView? = null
    private var mService: BackgroundLocationService? = null
    //private val mBroadcastManager : LocalBroadcastManager? = null
    //We load two data objects in this Fragment, the Run and a list of its locations, so we set up a
    //loader for a cursor of each of them so that the loading takes place on a different thread.
    private val mRunCursorLoaderCallbacks = RunCursorLoaderCallbacks()
    private val mLocationListCursorCallbacks = LocationListCursorCallbacks()
    private var mLocationCursor: Cursor? = null
    private var mGoogleMap: GoogleMap? = null
    private lateinit var mMapView: MapView
    private var mPolyline: Polyline? = null
    //Data structures needed to select and receive local broadcast messages sent by the Intent
    //Service
    private var mResultsFilter: IntentFilter? = null
    private var mResultsReceiver: ResultsReceiver? = null
    //ArrayList to hold the LatLngs needed to build a Polyline to display the Run's route in the map.
    private var mPoints: ArrayList<LatLng>? = null
    private var mLocationCount = 0
    //Bounds to define area a map for this run
    private var mBounds: LatLngBounds? = null
    private val mBuilder = LatLngBounds.Builder()
    //Designator for how to place the Camera on the map - default is to show the entire route as it's
    //generated
    private var mViewMode = Constants.SHOW_ENTIRE_ROUTE
    private var mPadding: Int = 0
    private var mZoom: Float = 0.toFloat()
    private var oldZoom: Float = 0.toFloat()
    //Are we tracking ANY Run?
    private var mStarted = false
    //Are we tracking THIS Run?
    private var mIsTrackingThisRun = false
    //Map marker for the end of the Run; this marker's location will change, so we make it a member
    //variable we can access from multiple methods.
    private var mEndMarker: Marker? = null
    //Have we initialized our initial conditions for the map?
    private var mPrepared = false
    //Are we bound to the BackgroundLocationService?
    private var mBound = false
    private var mShouldUpdate = false
    private var mConfigChanged = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setHasOptionsMenu set to "true" here, but invalidateFragmentsMenus() in RunPagerActivity
        //will set it to false if this Run isn't the one currently displayed in the Activity's ViewPager.
        setHasOptionsMenu(true)
        //Turn off DisplayHomeAsUpEnabled so that more of the ActionBar's subtitle will appear in portrait mode
        if ((if (activity as AppCompatActivity? != null) (activity as AppCompatActivity).supportActionBar else null) != null) {

            (activity as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        }
        val args = arguments
        if (args != null) {
            val runId = args.getLong(Constants.ARG_RUN_ID, -1)
            //If the run already has an id, it will have database records associated with it that
            //need to be loaded using Loaders.
            if (runId != -1L) {
                mRunId = runId
                mRun = RunManager.getRun(mRunId)
            }
            /*try {
                //noinspection ConstantConditions
                mPoints = new ArrayList<>(RunManager.retrievePoints(mRunId));
                //Log.i(TAG, "For Run #" + mRunId + " mPoints retrieved.");
            } catch (NullPointerException e){
                mPoints = new ArrayList<>();
                //Log.i(TAG, "For Run #" + mRunId + " created new ArrayList<LatLng> for mPoints.");
            }*/
            /*Initialize zoom level for tracking modes other than SHOW_ENTIRE_ROUTE. mZoom is for
             *current zoom level and oldZoom is to hold the last previous level, used to determine
             *whether a camera movement has caused a change in zoom level. Get value from
             *saveInstanceState if it's there, otherwise from SharedPrefs.
             */
            if (savedInstanceState != null) {
                mZoom = savedInstanceState.getFloat(Constants.ZOOM_LEVEL, 17.0f)
                oldZoom = mZoom
                Log.i(TAG, "Got $mZoom for mZoom from savedInstanceState in onCreate()")
            } else {
                mZoom = RunTracker2Kotlin.prefs!!.getFloat(Constants.ZOOM_LEVEL, 17.0f)
                oldZoom = mZoom
                Log.i(TAG, "Got $mZoom for mZoom from SharedPrefs in onCreate4()")
            }
            mRunManager = RunManager.get(context!!)
            mPoints = ArrayList()
            mBounds = RunManager.retrieveBounds(mRunId)
            mLocationCount = RunManager.getLocationCount(mRunId)
            //If we've already stored values for mBounds and mPoints in the RunManager singletons,
            //retrieve them on a separate thread to speed initialization of the map
            /*try {
                //noinspection ConstantConditions
                mPoints = new ArrayList<>(RunManager.retrievePoints(mRunId));
                //Log.i(TAG, "For Run #" + mRunId + " mPoints retrieved.");
            } catch (NullPointerException e){
                mPoints = new ArrayList<>();
                //Log.i(TAG, "For Run #" + mRunId + " created new ArrayList<LatLng> for mPoints.");
            }
            mBounds = RunManager.retrieveBounds(mRunId);
            mLocationCount = RunManager.getLocationCount(mRunId);
            //Log.i(TAG, "In onCreate for Run #" + mRunId +" is mBounds null? " + (mBounds == null));
            //If this is not a newly-created Run, load all the location information available for
            //Run into a LocationCursor a spawn a separate thread to fill mBounds and  mPoints  with
            //it.
            Cursor locationCursor = getContext().getContentResolver().query(
                    Constants.URI_TABLE_LOCATION,
                    null,
                    Constants.COLUMN_LOCATION_RUN_ID + " = ?",
                    new String[]{String.valueOf(mRunId)},
                    Constants.COLUMN_LOCATION_TIMESTAMP + " desc"
            );
            LoadPointsAndBounds initTask = new LoadPointsAndBounds(locationCursor, mLocationCount, mPoints, mBounds,
                    mBuilder);
            initTask.execute();*/
        }
        //Set up Broadcast Receiver to get reports of results from TrackingLocationIntentService
        //First set up the IntentFilter for the Receiver so it will receive the Intents intended for it
        mResultsFilter = IntentFilter(Constants.SEND_RESULT_ACTION)
        mResultsFilter!!.addAction(Constants.ACTION_REFRESH_UNITS)
        mResultsFilter!!.addAction(Constants.ACTION_REFRESH_MAPS)
        //Now instantiate the Broadcast Receiver
        mResultsReceiver = ResultsReceiver()
        sLocationSettingsRequest = locationSettingsRequest
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        /*Make sure the loaders are initialized for the newly-launched Run.
         *Loaders need to be initialized here because their life cycle is
         *actually tied to the Activity, not the Fragment. If initialized
         *earlier, we'll get runtime errors complaining that we're trying
         *to start loaders that have already been started and to stop loaders
         *that have already been stopped.*/
        val args = Bundle()
        args.putLong(Constants.ARG_RUN_ID, mRunId)
        //Start the loaders for the run and the last location
        loaderManager.initLoader(Constants.LOAD_RUN, args, mRunCursorLoaderCallbacks)
        loaderManager.initLoader(Constants.LOAD_LOCATION, args, mLocationListCursorCallbacks)
        /*Following is needed when the Activity is destroyed and recreated so that the Fragment
         *in the foreground will have a Run in mRun and thereby present the user with location
         *updates
         */
        if (RunManager.isTrackingRun && mRun == null) {
            mRun = Run()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val v = inflater.inflate(R.layout.combined_fragment, container, false)
        mStartedTextView = v.findViewById(R.id.run_startedTextView)
        mStartingPointTextView = v.findViewById(R.id.run_starting_pointTextView)
        mStartingAltitudeTextView = v.findViewById(R.id.run__starting_altitudeTextView)
        mStartingAddressTextView = v.findViewById(R.id.run_starting_addressTextView)

        mEndedTextView = v.findViewById(R.id.run_endedTextView)
        mEndingPointTextView = v.findViewById(R.id.ending_pointTextView)
        mEndingAltitudeTextView = v.findViewById(R.id.run__ending_altitudeTextView)
        mEndingAddressTextView = v.findViewById(R.id.run_ending_address_TextView)
        mDurationTextView = v.findViewById(R.id.run_durationTextView)
        mDistanceCoveredTextView = v.findViewById(R.id.distance_coveredTextView)

        mStartButton = v.findViewById(R.id.run_startButton)
        mStartButton!!.setOnClickListener { _ ->
            Log.i(TAG, " Pressed StartButton. Run Id is " + mRun!!.id)
            //Make sure Location Services is enabled and that we have permission to use it
            checkLocationSettings()
            if (!checkPermission()) {
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                        Constants.REQUEST_LOCATION_PERMISSIONS)
            }
            /*If we've got the necessary settings and permissions, start location updates and
             *perform housekeeping for tracking a Run.
             */
            if (RunTracker2Kotlin.locationSettingsState && checkPermission() && mBound) {
                mRunManager!!.startTrackingRun(mRunId)
                mService!!.startLocationUpdates(mRunId)
                /*Tracking a Run disables the New Run menu item, so we have to invalidate the
                 *Options Menu to update its appearance,
                 */
                activity!!.invalidateOptionsMenu()
                updateUI()

            }
        }
        mStopButton = v.findViewById(R.id.run_stopButton)
        mStopButton!!.setOnClickListener { _ ->
            Log.i(TAG, "Stop Button Pressed for Run " + mRunId)
            //Tell the service that controls Location updates to stop updates
            mService!!.stopLocationUpdates()
            //...and give up foreground status
            mService!!.stopForeground(true)
            /*The New Run menu item has been reenabled, so we must invalidate the Options Menu
             *to refresh its appearance.
             */
            activity!!.invalidateOptionsMenu()
            //Now that we've stopped tracking, do one last update of the Ending Address to catch
            //any change from the updates done automatically during tracking of Run
            if (mRun!!.endAddress != null) {
                RunManager.updateEndAddress(mRun!!)
            }
            updateUI()
        }

        //If this isn't a new run, we should immediately populate the textviews.
        //Start with information concerning the starting point.
        mStartLocation = RunManager.getStartLocationForRun(mRunId)
        if (mStartLocation != null) {
            mStartedTextView!!.text = Constants.DATE_FORMAT.format(mRun!!.startDate)
            //Report latitude and longitude in degrees, minutes and seconds
            mStartingPointTextView!!.text = RunManager.convertLocation(mStartLocation!!.latitude,
                    mStartLocation!!.longitude)
            /*Report altitude values in feet or meters, depending upon measurement units setting -
             *Imperial or metric.
             */
            mStartingAltitudeTextView!!.text = RunManager.formatAltitude(mStartLocation!!.altitude)
            //Load what this Run has in the database for its Starting Address
            mStartingAddressTextView!!.text = mRun!!.startAddress
            /*If what we're showing for the Starting Address is bad, try to get a good address from the
             *geocoder and record it to the database.
             */
            if (RunManager.addressBad(context!!, mStartingAddressTextView!!.text.toString()) && mBound) {
                RunManager.updateStartAddress(mRun!!)
            }
        }
        //Now display what we have concerning the ending point.
        mLastLocation = RunManager.getLastLocationForRun(mRunId)
        /*If we have a last location that's different from the starting location, display the data
         *we have concerning it.
         */
        if (mLastLocation != null && mLastLocation != mStartLocation) {
            mEndedTextView!!.text = Constants.DATE_FORMAT.format(mLastLocation!!.time)
            mEndedTextView!!.text = RunManager.convertLocation(mLastLocation!!.latitude,
                    mLastLocation!!.longitude)
            mEndingAltitudeTextView!!.text = RunManager.formatAltitude(mLastLocation!!.altitude)
            mEndingAddressTextView!!.text = mRun!!.endAddress
            /*If our Ending Address loaded from the database is bad, get a new value from the
             *geocoder and store it to the database,then display it.
            */
            if (RunManager.addressBad(context!!, mEndingAddressTextView!!.text.toString()) && mBound) {
                RunManager.updateEndAddress(mRun!!)
            }
            //Display duration of the Run in hours, minutes and seconds
            mDurationTextView!!.text = Run.formatDuration((mRun!!.duration / 1000).toInt())
            /*Display Run's distance in meters and kilometers or feet and miles, depending upon
             *measurement units setting.
             */
            mDistanceCoveredTextView!!.text = RunManager.formatDistance(mRun!!.distance)
        }
        //Enable Start button only if we're not tracking ANY run at this time
        mStartButton!!.isEnabled = !mStarted
        //Enable Stop button only if we're tracking THIS run
        mStopButton!!.isEnabled = mStarted && mIsTrackingThisRun

        //Now initialize the map
        mMapView = v.findViewById(R.id.mapViewContainer)
        mMapView.onCreate(savedInstanceState)
        mMapView.onResume() //needed to get map to display immediately

        try {
            MapsInitializer.initialize(activity!!.applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mMapView.getMapAsync { googleMap ->
            //Stash a reference to the GoogleMap
            mGoogleMap = googleMap
            if (mGoogleMap != null) {
                /*Rather than define our own zoom controls, just enable the UiSettings' zoom
                 *controls and listen for changes in CameraPosition to update mZoomLevel.
                 */
                mGoogleMap!!.uiSettings.isZoomControlsEnabled = true
                mGoogleMap!!.uiSettings.isZoomGesturesEnabled = true
                /*Disable map scrolling so we can easily swipe from one map to another. This setting
                 *can be changed in the Preferences menu. While tracking a Run, scrolling for its
                 *map cannot be enabled for tracking modes that fix the camera's position on the
                 *basis of changing values (i.e., SHOW_ENTIRE_ROUTE and FOLLOW_END_POINT.
                 */
                mGoogleMap!!.uiSettings.isScrollGesturesEnabled = false
                /*Set up an overlay on the map for the two markers we use for each map. We need a
                 *custom InfoWindowAdapter to allow multiline text snippets for the markers.*/
                mGoogleMap!!.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
                    override fun getInfoWindow(marker: Marker): View? = null

                    //Define layout for markers' information windows.
                    override fun getInfoContents(marker: Marker): View {
                        /*Set up vertical Linear Layout to hold TextViews for a marker's Title and
                         *Snippet fields.
                         */
                        val layout = LinearLayout(activity)
                        layout.orientation = LinearLayout.VERTICAL
                        //Define TextView with desired text attributes for the marker's Title
                        val title = TextView(activity)
                        title.setTextColor(Color.BLACK)
                        title.gravity = Gravity.CENTER
                        title.setTypeface(null, Typeface.BOLD)
                        title.text = marker.title
                        //Define TextView with desired text attributes for the marker's Snippet
                        val snippet = TextView(activity)
                        snippet.setTextColor(Color.BLACK)
                        snippet.text = marker.snippet
                        /*//Add the TextViews to the Linear Layout and return the layout for addition
                         *to the Fragment's overall View hierarchy.
                         */
                        layout.addView(title)
                        layout.addView(snippet)
                        return layout
                    }
                })
                /*Set up a listener for when the user clicks on the End Marker. We update its
                 *snippet while tracking this run only when the user clicks on it to avoid the
                 *overhead of updating the EndAddress on every location update. If we're not
                 *tracking the run,just load the end address from the database. The Start Marker's
                 *data never changes, so the listener can ignore clicks on it and simply allow the
                 *default behavior to occur.
                 */
                mGoogleMap!!.setOnMarkerClickListener { marker ->
                    if (marker == mEndMarker) {
                        var endDate = ""
                        if (RunManager.getLastLocationForRun(mRunId) != null) {
                            endDate = Constants.DATE_FORMAT.format(
                                    RunManager.getLastLocationForRun(mRunId)!!.time)
                        }
                        val snippetAddress = when (RunManager.isTrackingRun(RunManager.getRun(mRunId))) {
                            true -> RunManager.getAddress(activity, marker.position)
                            false -> RunManager.getRun(mRunId)!!.endAddress
                        }
                        /*if (RunManager.isTrackingRun(RunManager.getRun(mRunId))) {
                            snippetAddress = RunManager.getAddress(activity, marker.position)
                        } else {
                            snippetAddress = RunManager.getRun(mRunId)!!.endAddress
                        }*/
                        marker.snippet = endDate + "\n" + snippetAddress
                    }
                    /*Need to return "false" so the default action for clicking on a marker will
                     *also occur for the Start Marker and for the End Marker after we've updated its
                     *snippet.
                     */
                    false
                }
                /*Set up three listeners on the camera to change zoom levels on all tracking modes
                 *other than SHOW_ENTIRE_ROUTE. The "Started" listener detects when a camera move
                 *begins and singles out those caused by API_ANIMATION - moves caused by pressing
                 *map control widgets such as the zoom controls - for further action. The "Move"
                 *listener checks whether the camera's current zoom level of the camera after the
                 *move is different than the last previous level (oldZoom), If so, the current mZoom
                 *value is assigned to oldZoom and the camera's current zoom level is copied to
                 *mZoom for use in positioning the camera as we track an on-going Run. The "Idle"
                 *listener detects when a camera movement has ended, checks whether the movement was
                 *one that produced a change in zoom level (i.e., the user has stopped pressing the
                 *zoom controls) and notifies all other open CombinedFragments to update their maps
                 *using the new zoom value stored in SharedPrefs.
                 *
                 *One remaining annoyance is that the system asynchronously updates the camera
                 *values once per frame (60 Hz), so changes to the zoom level the user makes may not
                 *get copied to mZoom before the next frame, which in turn will cause the listener
                 *to detect a false "update" back to the old zoom value. That will cause the map
                 *to zoom back to the previous zoom level after the map briefly displays the new
                 *zoom level, even though the user has not hit the zoom controls again. To ensure
                 *that zoom changes will "stick," the zoom controls must be pressed several times in
                 *succession. There is no control over how many of the presses will "stick," which
                 *makes control over the zoom level of a Run that being tracked very inexact.
                 */
                mGoogleMap!!.setOnCameraMoveStartedListener { reason: Int ->

                    if (reason == REASON_API_ANIMATION) {
                        if (mViewMode != Constants.SHOW_ENTIRE_ROUTE) {

                            mShouldUpdate = true
                            Log.i(TAG, "onCameraMoveStarted() called with REASON_API_ANIMATION. mShouldUpdate is true")
                        }
                    }
                }
                mGoogleMap!!.setOnCameraMoveListener {
                    if (mShouldUpdate && mGoogleMap!!.cameraPosition.zoom != oldZoom) {
                        //mZoom = mGoogleMap.getCameraPosition().zoom;
                        oldZoom = mZoom
                        if (mGoogleMap!!.cameraPosition.zoom > oldZoom) {
                            mZoom += 0.25f
                        } else {
                            mZoom -= 0.25f
                        }
                    }
                }
                mGoogleMap!!.setOnCameraIdleListener {
                    if (mShouldUpdate) {
                        RunTracker2Kotlin.prefs!!.edit().putFloat(Constants.ZOOM_LEVEL,
                                mZoom).apply()
                        val trackingModeIntent = Intent(Constants.SEND_RESULT_ACTION)
                                .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_REFRESH_MAPS)
                                .putExtra(Constants.ARG_RUN_ID, mRunId)
                        val receiver = LocalBroadcastManager.getInstance(context!!).sendBroadcast(trackingModeIntent)
                        if (!receiver) {
                            Log.i(TAG, "No receiver for trackingModeIntent!")
                        }
                        mShouldUpdate = false
                    }

                }
            }
        }
        return v
    }

    override fun onPrepareOptionsMenu(menu: Menu?) {
        super.onPrepareOptionsMenu(menu)
        //Set text of menuItem to select distance measurement units according to its current setting.
        if (RunTracker2Kotlin.prefs!!.getBoolean(Constants.MEASUREMENT_SYSTEM, Constants.IMPERIAL)) {
            menu!!.findItem(R.id.run_map_pager_activity_units).setTitle(R.string.imperial)
        } else {
            menu!!.findItem(R.id.run_map_pager_activity_units).setTitle(R.string.metric)
        }
        //Set text of menuItem to turn scrolling on or off according to its current setting.
        if (mGoogleMap != null && mGoogleMap!!.uiSettings != null) {
            if (mGoogleMap!!.uiSettings.isScrollGesturesEnabled) {
                menu.findItem(R.id.run_map_pager_activity_scroll)
                        .setTitle(R.string.map_scrolling_off)
            } else {
                menu.findItem(R.id.run_map_pager_activity_scroll)
                        .setTitle(R.string.map_scrolling_on)
            }
        }
        /*If the Run's being tracked and the ViewMode is SHOW_ENTIRE_ROUTE, FOLLOW_END_POINT or
         *FOLLOW_START_POINT scrolling won't work because the map gets a new CameraUpdate with a
         *LatLngBounds or a LatLng that specifies the center of the map that pulls the display back
         *and undoes the scrolling with every location update.
         */
        if (RunManager.isTrackingRun(RunManager.getRun(mRunId)) && !RunTracker2Kotlin.prefs!!.getBoolean(Constants.SCROLLABLE, false)) {
            menu.findItem(R.id.run_map_pager_activity_scroll)
                    .setEnabled(false)
                    .setTitle(R.string.map_scrolling_on)
        }
    }

    /*
    *Settings that change how Runs' data are displayed are placed in the CombinedFragment's
    *OptionsMenu. This includes the selection of Imperial or Metric measurement units, turning map
    *scrolling on or off, and how to track Runs.
    *
    *The Options Menu allows the user to specify whether to track the whole route (the default),
    *track the ending point of the route, track the starting point of the route, or turn off
    *tracking. Actually changing the tracking mode is done in the separate method setTrackingMode()
    *so that the last previously selected tracking mode can be used when the map is reopened instead
    *of always starting with the default SHOW_ENTIRE_ROUTE.*/
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.run_map_pager_activity_units  -> {
                //Swap distance measurement unit between imperial and metric
                RunTracker2Kotlin.prefs!!.edit().putBoolean(Constants.MEASUREMENT_SYSTEM,
                        !RunTracker2Kotlin.prefs!!.getBoolean(Constants.MEASUREMENT_SYSTEM,
                                Constants.IMPERIAL))
                        .apply()
                /*Send a broadcast to all open CombinedFragments will update their displays to show
                 *the newly-selected distance measurement units.
                 */
                val refreshIntent = Intent(Constants.ACTION_REFRESH_UNITS)
                refreshIntent.putExtra(Constants.ARG_RUN_ID, mRunId)
                val receiver = LocalBroadcastManager.getInstance(context!!).sendBroadcast(refreshIntent)
                if (!receiver) {
                    Log.i(TAG, "No receiver for CombinedRunFragment REFRESH broadcast!")
                }
                /*Now force the OptionsMenu to be redrawn to switch the display of the measurement
                 *units menuItem.
                 */
                activity!!.invalidateOptionsMenu()
                Log.i(TAG, "Calling updateUI() in Units menuitem.")
                updateUI()
                return true
            }
            R.id.run_map_pager_activity_scroll -> {
                //Change scroll setting for the currently displayed map only.
                if (mGoogleMap != null && RunTracker2Kotlin.prefs!!.getLong(Constants.ARG_RUN_ID, -1) == mRunId) {
                    //Toggle the ability to scroll the map.
                    mGoogleMap!!.uiSettings.isScrollGesturesEnabled = !mGoogleMap!!.uiSettings
                            .isScrollGesturesEnabled
                    //Store current state of scrolling.
                    RunTracker2Kotlin.prefs!!.edit().putBoolean(Constants.SCROLL_ON,
                            !RunTracker2Kotlin.prefs!!.getBoolean(Constants.SCROLL_ON, false))
                            .apply()
                }
                /*We want to change the menuItem title in onPrepareOptionsMenu, so we need to
                 *invalidate the menu and recreate it.
                 */
                activity!!.invalidateOptionsMenu()
                updateUI()
                return true
            }
        /*Select desired map updating mode, then call setTrackingMode() to act on it. We use a
             *separate function for setTrackingMode() so that it can be invoked when the fragment
             *restarts with the last previous tracking mode still in effect, rather than going with
             *the default of SHOW_ENTIRE_ROUTE.
             */
            R.id.show_entire_route_menu_item   -> {
                mViewMode = Constants.SHOW_ENTIRE_ROUTE
                if (RunManager.isTrackingRun(mRun)) {
                    /*Scrolling won't work with this tracking mode while the run is actually being
                     *tracked, so turn it off with the next update of the UI.
                     */
                    RunTracker2Kotlin.prefs!!.edit().putBoolean(Constants.SCROLLABLE, false).apply()
                }
            }
            R.id.track_end_point_menu_item     -> {
                mViewMode = Constants.FOLLOW_END_POINT
                if (RunManager.isTrackingRun(mRun)) {
                    /*Scrolling won't work with this tracking mode while the run is actually being
                     *tracked, so turn it off with the next update of the UI.
                     */
                    RunTracker2Kotlin.prefs!!.edit().putBoolean(Constants.SCROLLABLE, false).apply()
                }
            }
            R.id.track_start_point_menu_item   -> {
                mViewMode = Constants.FOLLOW_STARTING_POINT
                if (RunManager.isTrackingRun(mRun)) {
                    /*Scrolling won't work with this tracking mode while the run is actually being
                     *tracked, so turn it off with the next update of the UI.
                     */
                    RunTracker2Kotlin.prefs!!.edit().putBoolean(Constants.SCROLLABLE, false).apply()
                }
            }
            R.id.tracking_off_menu_item        -> {
                mViewMode = Constants.NO_UPDATES
                /*Scrolling will work with this tracking mode, so make sure we enable the scrolling
                 *menuItem upon next update of UI.
                 */
                RunTracker2Kotlin.prefs!!.edit().putBoolean(Constants.SCROLLABLE, true).apply()
            }
            else                               -> return super.onOptionsItemSelected(item)
        }
        /*Store and implement the new tracking mode and tell all the other open CombinedFragments to
         *switch to this mode.
         */
        RunTracker2Kotlin.prefs!!.edit().putInt(Constants.TRACKING_MODE, mViewMode).apply()
        setTrackingMode()
        val trackingModeIntent = Intent(Constants.SEND_RESULT_ACTION)
                .putExtra(Constants.ACTION_ATTEMPTED, Constants.ACTION_REFRESH_MAPS)
                .putExtra(Constants.ARG_RUN_ID, mRunId)
        val receiver = LocalBroadcastManager.getInstance(context!!).sendBroadcast(trackingModeIntent)
        if (!receiver) {
            Log.i(TAG, "No receiver for trackingModeIntent!")
        }
        //Need to update the menu's appearance after enabling or disabling Scroll menu item
        activity!!.invalidateOptionsMenu()
        //Update the UI to reflect the new view/tracking mode.
        updateUI()
        return true
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        mConfigChanged = true
    }

    private fun updateUI() {
        Log.i(TAG, "Entered updateUI()")
        /*It's possible for a Fragment to try to update its state when not attached to the under-
         *lying Activity - result then is crash!
         */
        if (isAdded) {
            /*Are we tracking ANY run? We call the RunManager method because the PendingIntent that
             *the BackgroundLocationService uses to request and remove location updates is supplied
             *by RunManager's getLocationPendingIntent(boolean) method.
             */
            mStarted = RunManager.isTrackingRun
            Log.i(TAG, "mStarted is " + mStarted)
            //Are we tracking THIS run?
            mIsTrackingThisRun = RunManager.isTrackingRun(mRun)
            Log.i(TAG, "mIsTrackingThisRun is " + RunManager.isTrackingRun(mRun))
            //Enable Start button only if we're not tracking ANY run at this time
            val startCheck = mStartButton!!.isEnabled
            Log.i(TAG, "startCheck is " + startCheck + " and mConfigChanged is " + mConfigChanged)
            mStartButton!!.isEnabled = !mStarted
            //Enable Stop button only if we're tracking and tracking THIS run
            mStopButton!!.isEnabled = mStarted && mIsTrackingThisRun

            if (mRun == null) {
                return
            }

            /*If we haven't yet gotten a starting location for this run, try to get one. Once we've
             *gotten a starting location, no need to ask for it again unless it's "bad," which we
             *check for later in this method. Accordingly, everything in the following if statement
             *gets executed only once by a Run getting tracked.
             */
            if (mStartLocation == null) {
                Log.i(TAG, "New Run - no mStartLocation yet")
                /*This is a  brand new Run with no locations yet picked up - get the Starting
                 *Location, if it exists.
                 */
                mStartLocation = RunManager.getStartLocationForRun(mRunId)
                if (mStartLocation != null) {
                    Log.i(TAG, "Now have mStartLocation")
                    /*Now that we've gotten a Starting Location, record and display information
                     *about it. Change the start date to the timestamp of the first Location object
                     *received.
                     */
                    mRun!!.startDate = Date(mStartLocation!!.time)
                    //Now write the new start date to the database
                    RunManager.updateStartDate(mRun!!)
                    //Finally, display the new start date
                    mStartedTextView!!.text = Constants.DATE_FORMAT.format(mRun!!.startDate)
                    //Display in appropriate format the starting point for the Run
                    mStartingPointTextView!!.text = RunManager.convertLocation(mStartLocation!!.latitude,
                            mStartLocation!!.longitude)
                    //Now update display of the altitude of the starting point.
                    mStartingAltitudeTextView!!.text = RunManager.formatAltitude(mStartLocation!!.altitude)
                    //We won't have a Starting Address yet, so ask for one and record it.
                    RunManager.updateStartAddress(mRun!!)
                    mStartingAddressTextView!!.text = mRun!!.startAddress
                    /*If we get here, mBounds should be null, but better to check. Put the starting
                     *location into the LatLngBounds Builder and later, when at least one additional
                     *location has also been included, build mBounds.
                     */
                    if (mBounds == null) {
                        mBuilder.include(LatLng(mStartLocation!!.latitude,
                                mStartLocation!!.longitude))
                    }
                    /*If we get here, mPoints should have zero elements, but better to check, then
                     *add the mStartLocation as the first element.
                     */
                    if (mPoints!!.size == 0) {
                        mPoints!!.add(LatLng(mStartLocation!!.latitude,
                                mStartLocation!!.longitude))
                    }
                } else {
                    Log.i(TAG, "getStartLocationForRun returned null for Run " + mRun!!.id)
                }
            }
            /*If we have a starting location but don't yet have a good starting address, get one and
             *update the Run Table with a new starting date equal to the time of the first location
             *and with the new starting address. Once we have a starting address,no need to reload
             *any data concerning the Start Location - it won't change as the Run goes on.
             */
            if (mRun != null && mStartLocation != null && mLocationCursor != null && !mLocationCursor!!.isClosed) {
                /*We need to initialize mLastLocation here so that we can update the
                 *EndingAltitudeTextView for a Run that's not being tracked, and therefore getting
                 *mLastLocation set in the LocationCursor loader as new locations come in, when the
                 *user switches between Metric and Imperial measurement units. For this purpose, it
                 *makes no difference if there is only one location for the Run and therefore
                 *mStartLocation and mLastLocation are identical in value.
                 */
                //mLastLocation = RunManager.getLastLocationForRun(mRunId);
                mLocationCursor!!.moveToLast()
                mLastLocation = RunDatabaseHelper.getLocation(mLocationCursor!!)
                if (RunManager.addressBad(context!!,
                        mStartingAddressTextView!!.text.toString())) {
                    /*Get the starting address from the geocoder, record that in the Run Table and
                     *display it in the TextView.
                     */
                    RunManager.updateStartAddress(mRun!!)
                    mStartingAddressTextView!!.text = mRun!!.startAddress
                }
                /*Starting and Ending Altitudes need to get reset when the measurement units get
                 *changed while the user tracks the Run, so they need to be checked/updated on every
                 *UI update.
                 */
                mStartingAltitudeTextView!!.text = RunManager.formatAltitude(mStartLocation!!.altitude)
                if (mLastLocation != null) {
                    mEndingAltitudeTextView!!.text = RunManager.formatAltitude(mLastLocation!!.altitude)
                }
            }
            /*mLastLocation gets reset when mLocationCursor is returned from the LocationListLoader.
             *That generates changes to the data stored for the Run, including updated Duration and
             *Distance, so the RunCursorLoader will also produce a new RunCursor. If we have a Run
             *and a last location for it, we will have duration and distance values for it in the
             *Run Table, so retrieve and display them. This has to be done every time a new location
             *is recorded and, accordingly, the UI updates.
             */
            if (mRun != null && mLastLocation != null && mLastLocation != mStartLocation) {
                Log.i(TAG, "Got a good mLastLocation - updating accordingly")
                mDurationTextView!!.text = Run.formatDuration((mRun!!.duration / 1000).toInt())
                mDistanceCoveredTextView!!.text = RunManager.formatDistance(mRun!!.distance)

                mEndingPointTextView!!.text = RunManager.convertLocation(mLastLocation!!.latitude,
                        mLastLocation!!.longitude)
                mEndedTextView!!.text = Constants.DATE_FORMAT.format(mLastLocation!!.time)
                /*We don't check for bad Ending Addresses because the Ending Address gets updated
                 *every ten second while the Run is being tracked.
                 */
                mEndingAddressTextView!!.text = mRun!!.endAddress

                /*If mBounds hasn't been initialized yet, add this location to the Builder and
                 *create mBounds. If mBounds has been created, simply add this point to it and save
                 *the newly-updated mBounds to the RunManager singleton for use in recreating the
                 *map for a Run being tracked more quickly upon a configuration change (feature not
                 *yet implemented).
                 */
                val latLng = LatLng(mLastLocation!!.latitude, mLastLocation!!.longitude)
                if (mBounds == null) {
                    mBuilder.include(latLng)
                    mBounds = mBuilder.build()
                    RunManager.saveBounds(mRunId, mBounds!!)
                } else {
                    if (!mBounds!!.contains(latLng)) {
                        mBounds = mBounds!!.including(latLng)
                        RunManager.saveBounds(mRunId, mBounds!!)
                    }
                }
                /*If we've already initialized the map for this CombinedFragment, all we need to do
                 *is update it using the latest location update's data - no need to redo map
                 *initialization steps.
                 */
                if (mPrepared) {
                    /*Check to see if the newly received location is different from the last previous
                     *one; if it is, set it as the final location value to animate to from the last
                     *previous one over what should be the one second time difference between them.
                     *We use a ValueAnimator to fix the position update values for the Polyline and
                     *the EndMarker between the two location updates from the Location Service
                     *because mPoints does not have a "property" which can be interpolated - it
                     *has an element added for each new location to plot.
                     */
                    if (mPoints!!.size >= 2) {
                        Log.i(TAG, "Map is prepared and we have at least two points for the polyline")

                        if (mLastLocation!!.latitude != mPoints!![mPoints!!.size - 1].latitude || mLastLocation!!.longitude != mPoints!![mPoints!!.size - 1].longitude) {
                            Log.i(TAG, "New location is different - now updating map")
                            val previousUpdateTimeStamp = mPreviousLocation!!.time
                            /*Get time elapsed from last previous location to mLastLocation to derive
                             *length of animation; multiply by 1.2 to improve the animation.
                             */
                            val currentUpdateTimeStamp = mLastLocation!!.time
                            val updateDuration = ((currentUpdateTimeStamp - previousUpdateTimeStamp) * 1.2f).toLong()
                            /*Updates will be close together, so a linear interpolator is sufficient and
                             *much less  computationally taxing than the more accurate spherical
                             *interpolator.
                             */
                            val interpolator = LatLngInterpolator.LinearFixed()
                            /*Use an ValueAnimator to derive interim LatLng Points and move
                             *EndMarker, mPoints and Camera to them to attempt smooth movement.
                             */
                            val markerAnimator = ValueAnimator()
                            Log.i(TAG, "Starting animator")
                            markerAnimator.addUpdateListener { _ ->
                                val fraction = markerAnimator.animatedFraction
                                val newPosition = interpolator.interpolate(fraction,
                                        mPoints!![mPoints!!.size - 2],
                                        latLng)
                                mEndMarker!!.position = newPosition
                                val movement = updateCamera(mViewMode, newPosition)
                                if (movement != null) {
                                    mGoogleMap!!.moveCamera(movement)
                                }
                                mPoints!!.add(newPosition)
                                mPolyline!!.points = mPoints
                            }
                            markerAnimator.setFloatValues(0.0f, 1.0f)
                            markerAnimator.duration = updateDuration
                            markerAnimator.start()
                            //updateLocationMarker(SystemClock.uptimeMillis(), mPoints.get(mPoints.size() - 1), latLng);
                            RunManager.savePoints(mRunId, mPoints!!)
                            mConfigChanged = false
                        }
                    }
                } else {
                    //If we haven't yet initialized the map with our starting conditions, do it now.
                    prepareMap()
                }
            }
        } else {
            Log.i(TAG, "Fragment is not Added() - cannot update UI")
        }
    }

    private fun updateCamera(mode: Int, latLng: LatLng): CameraUpdate? {
        /*This method will be called after every location update and move the Camera's location
         *according to the map updating mode selected by the user and the current location of
         *the Run's end point.
         */
        if (mRunId != RunManager.currentRunId) {
            //Camera updates are relevant only to Runs being currently tracked.
            return null
        }
        val cameraUpdate: CameraUpdate?
        when (mode) {
            Constants.SHOW_ENTIRE_ROUTE     -> {
                /*To show the entire route, we supply the newly-updated mBounds containing all the
                *LatLngs in the route to the relevant CameraUpdateFactory method. The map has
                *already been created, so we don't need to tell the CameraUpdate about the size of
                *the map.
                */
                cameraUpdate = CameraUpdateFactory.newLatLngBounds(mBounds, mPadding)
            }
            Constants.FOLLOW_END_POINT      -> {
                /*To track the end point of the Run, move the camera to the new end point at the
                 *zoom level last used for this mode or FOLLOW_START_POINT mode.
                 */
                cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, mZoom)
            }
            Constants.FOLLOW_STARTING_POINT -> {
                /*To center on the start point of the Run, move the camera to the starting point at
                 *the zoom level last set for this mode or FOLLOW_END_POINT mode.
                 */
                cameraUpdate = CameraUpdateFactory.newLatLngZoom(mPoints!![0], mZoom)
            }
            Constants.NO_UPDATES            -> {
                /*To turn off tracking, return a null so that the Camera will stay where it was
                 *following the previous location update.
                 */
                cameraUpdate = null
            }
            else                            -> cameraUpdate = null
        }
        return cameraUpdate
    }

    override fun onStart() {
        super.onStart()
        //Bind to the BackgroundLocationService here and unBind in onStop()
        if (mService == null) {
            mService = BackgroundLocationService()
        }
        context!!.bindService(Intent(context, BackgroundLocationService::class.java), mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        restartLoaders()
        mMapView.onResume()
        LocalBroadcastManager.getInstance(context!!).registerReceiver(mResultsReceiver!!, mResultsFilter!!)
        mPrepared = false
    }

    //Reload the location data for this run, thus forcing an update of the map display
    private fun restartLoaders() {
        val args = arguments
        if (args != null) {
            val runId = args.getLong(Constants.ARG_RUN_ID, -1)
            if (runId != -1L) {
                loaderManager.restartLoader(Constants.LOAD_LOCATION, args, LocationListCursorCallbacks())
                loaderManager.restartLoader(Constants.LOAD_RUN, args, RunCursorLoaderCallbacks())
            }
        }
    }

    override fun onPause() {
        mMapView.onPause()
        LocalBroadcastManager.getInstance(context!!).unregisterReceiver(mResultsReceiver!!)
        super.onPause()
    }

    override fun onDestroy() {
        mMapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        mMapView.onLowMemory()
        super.onLowMemory()
    }

    override fun onStop() {
        /*Clear map adornments to ensure that duplicate, stale end markers won't appear on the map
         *when the map gets displayed again.
         */
        if (mGoogleMap != null)
            mGoogleMap!!.clear()
        //Close the location cursor before shutting down
        if (mLocationCursor != null) {
            if (!mLocationCursor!!.isClosed)
                mLocationCursor!!.close()
        }
        //UnBind from the BackgroundLocationService here and Bind in onStart()
        context!!.unbindService(mServiceConnection)
        mBound = false

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putFloat(Constants.ZOOM_LEVEL, mZoom)
    }

    //Method to initialize the map
    private fun prepareMap() {

        /*We can't prepare the map until we actually get a map and an open location cursor with at
         *least two locations. We should also exit if the map has already been prepared,
         */
        if (mGoogleMap == null || mLocationCursor == null ||
                mLocationCursor!!.isClosed || mLocationCursor!!.count < 2 ||
                mPrepared) {
            return
        }
        Log.i(TAG, "Now preparing map")
        val r = resources
        mPadding = calculatePadding()
        /*If we've already stored values for mBounds and mPoints in the RunManager singletons,
         *retrieve them on a separate thread to speed initialization of the map. This feature
         *currently disabled.

        Log.d(TAG, "At beginning of prepareMap() for Run " + mRunId + ", mPoints.size() is " + mPoints.size() +
                " and mLocationCursor.getCount() is " + mLocationCursor.getCount());
        */
        /*We need to use the LocationCursor for the map markers because we need time data, which the
         *LatLng objects in mPoints lack.
         */
        mLocationCursor!!.moveToFirst()
        mStartLocation = getLocation(mLocationCursor!!)
        if (mBounds == null) {
            Log.i(TAG, "In prepareMap() for Run " + mRunId +
                    ", mBounds is null.")
            mBuilder.include(LatLng(mStartLocation!!.latitude,
                    mStartLocation!!.longitude))
            mBounds = mBuilder.build()
        } else {
            if (mStartLocation != null) {
                mBounds!!.including(LatLng(mStartLocation!!.latitude,
                        mStartLocation!!.longitude))
            }
        }
        //Put the first location in the first position in LatLng ArrayList used to make the Polyline.
        if (mStartLocation != null) {

            mPoints!!.add(0, LatLng(mStartLocation!!.latitude,
                    mStartLocation!!.longitude))
        }
        val startDate = Constants.DATE_FORMAT.format(if (mStartLocation != null) mStartLocation!!.time else 0)

        /*Now set up the EndMarker We use the default red icon for the EndMarker. We need to keep a
         *reference to it because it will be updated as the Run progresses.
         */
        var endDate = ""
        try {
            endDate = Constants.DATE_FORMAT.format(if (mLastLocation != null) mLastLocation!!.time else 0)
        } catch (npe: NullPointerException) {
            Log.e(TAG, "Caught an NPE trying to get the time of a LastLocation")
        }

        /*Get the address where the run ended from the database. If that address is bad, get a new
         *end address from the geocoder. The geocoder needs a LatLng, so we feed it the last element
         *in mPoints.
         */
        var snippetAddress = RunManager.getRun(mRunId)!!.endAddress
        if (RunManager.addressBad(context!!, snippetAddress!!)) {
            snippetAddress = RunManager.getAddress(activity, mPoints!![mPoints!!.size - 1])
        }
        val endMarkerOptions = MarkerOptions()
                .position(LatLng(mLastLocation!!.latitude, mLastLocation!!.longitude))
                .title(r.getString(R.string.run_finish))
                .snippet(endDate + "\n" + snippetAddress)
                .flat(false)
        /*Update mPoints, mPolyline, mBounds and the position of mEndMarker with additional location
         *data if the number of locations in the cursor exceeds the number of locations stored
         *in mBounds and mPoints.
         */
        var latLng: LatLng? = null
        Log.i(TAG, "Now reading location cursor to create mPoints and build polyline")
        while (!mLocationCursor!!.isAfterLast) {
            mLocationCursor!!.moveToNext()
            mLastLocation = getLocation(mLocationCursor!!)
            try {
                latLng = LatLng(if (mLastLocation != null) mLastLocation!!.latitude else 0.0,
                        if (mLastLocation != null) mLastLocation!!.longitude else 0.0)
                if (latLng.latitude != 0.0 && latLng.longitude != 0.0 &&
                        latLng.latitude != mPoints!![mPoints!!.size - 1].latitude &&
                        latLng.longitude != mPoints!![mPoints!!.size - 1].longitude) {
                    mPoints!!.add(latLng)
                    mBounds = mBounds!!.including(latLng)
                }

            } catch (NPE: NullPointerException) {
                Log.e(TAG, "Caught an NPE trying to extract a LatLng from a LastLocation")
            }

            if (mLocationCursor!!.isLast && latLng != null) {
                endMarkerOptions.position(latLng)
                endMarkerOptions.snippet(RunManager.getAddress(activity, latLng))
            }
        }
        Log.d(TAG, "Before prepareMap() code updating mPoints for Run " + mRunId + ", mLocationCursor.getCount() is " +
                mLocationCursor!!.count + " and mLocationCount is " + mLocationCount +
                ". mPoints.size() is " + mPoints!!.size)
        /*Feature to speed filling of mPoints and mBounds for currently tracked Run that has come
         *back to the foreground by retrieving the LatLngs saved to RunManager singletons during the
         *Run's former time in the foreground is not yet implemented, so code to do this is commented
         *out.
         */
        //if (mLocationCursor.getCount() > mPoints.size()){
        /*if (mLocationCursor.getCount() > mLocationCount){
            Log.i(TAG, "In prepareMap() for Run " + mRunId +", mLocationCursor.getCount() is " +
                            mLocationCursor.getCount() + " and mLocationCount is " + mLocationCount);
            //Set the cursor to the first location after the locations already stored in mPoints.
            mLocationCursor.moveToPosition(mLocationCount);

            LatLng latLng = null;
            //Iterate over the remaining location points in the cursor, updating mPoints, mPolyline,
            //and mBounds; fix position of mEndMarker when we get to the last entry in the cursor. We
            //won't get interpolated values between the locations in the cursor, but that makes no
            //difference here - interpolated values are needed only for "live" updates,not rebuilding
            //a Run we're coming back to.
            while (!mLocationCursor.isAfterLast()){
                mLastLocation = getLocation(mLocationCursor);
                try {
                    latLng = new LatLng(mLastLocation != null ? mLastLocation.getLatitude() : 0,
                                        mLastLocation != null ? mLastLocation.getLongitude() : 0);
                    if (latLng.latitude != 0 && latLng.longitude !=0) {
                        mPoints.add(latLng);
                        mBounds = mBounds.including(latLng);
                    }

                } catch (NullPointerException NPE){
                    Log.e(TAG, "Caught an NPE trying to extract a LatLng from a LastLocation");
                }
                if (mLocationCursor.isLast() && latLng != null){
                    endMarkerOptions.position(latLng);
                    endMarkerOptions.snippet(RunManager.getAddress(getActivity(), latLng));
                }
                mLocationCursor.moveToNext();
            }
            Log.d(TAG, "After adding additional LatLngs from cursor in prepareMap() for Run " + mRunId + ", mPoints.size() is + " + mPoints.size());
        }*/
        /*Get the address where we started the Run from the database. If the database's StartAddress
         *is bad, get a new Starting Address from the geocoder. The geocoder needs a LatLng object,
         *so use the first element of the mPoints List.
         */
        snippetAddress = RunManager.getRun(mRunId)!!.startAddress
        if (RunManager.addressBad(context!!, snippetAddress!!)) {
            snippetAddress = RunManager.getAddress(context, mPoints!![0])
        }
        /*Now create a marker for the starting point and put it on the map. The starting marker
         *doesn't need to be updated, so we don't even need to keep the return value from the call
         *to mGoogleMap.addMarker().
         */
        val startMarkerOptions = MarkerOptions()
                .position(mPoints!![0])
                .title(r.getString(R.string.run_start))
                .snippet(startDate + "\n" + snippetAddress)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .flat(false)
        mGoogleMap!!.addMarker(startMarkerOptions)
        val line = PolylineOptions()
        /*mPoints is the List of LatLngs used to create the mPolyline and in as as yet unimplemented
         *feature will be stored in a RunManager singleton. mPoints represents all the location data
         *collected for this Run.
         */

        //Now set up an initial CameraUpdate according to the tracking mode we're in.
        Log.i(TAG, "Now setting up initial CameraUpdate for the map")
        mViewMode = when (mPoints!!.size < 3) {
            true -> Constants.SHOW_ENTIRE_ROUTE
            false -> RunTracker2Kotlin.prefs!!.getInt(Constants.TRACKING_MODE,
                    Constants.SHOW_ENTIRE_ROUTE)
        }
        /*if (mPoints!!.size < 3) {
            mViewMode = Constants.SHOW_ENTIRE_ROUTE
        } else {
            mViewMode = RunTracker2Kotlin.prefs!!.getInt(Constants.TRACKING_MODE,
                    Constants.SHOW_ENTIRE_ROUTE)
        }*/
        Log.i(TAG, "In prepareMap() for Run " + mRunId +
                ", first LatLng is " + mPoints!![0].toString() +
                " and last LatLng is " + mPoints!![mPoints!!.size - 1])
        line.addAll(mPoints!!)
        mPolyline = mGoogleMap!!.addPolyline(line)
        //Now that we've fixed the position of mEndMarker, add it to the map.
        mEndMarker = mGoogleMap!!.addMarker(endMarkerOptions)
        /*Position the Camera according to the selected view mode. For the modes other than
         *SHOW_ENTIRE_ROUTE, moveCamera() is required in place of animateCamera because otherwise
         *the initial zoom level will be set at 2.0 to allow a "zoom in" and the initial CameraUpdate
         *will set that value in SharedPrefs where it will remain until the user manually zooms in.
         */
        when (mViewMode) {
            Constants.SHOW_ENTIRE_ROUTE     -> mGoogleMap!!.animateCamera(CameraUpdateFactory.newLatLngBounds(mBounds, mPadding))
            Constants.FOLLOW_END_POINT      -> mGoogleMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(mPoints!![mPoints!!.size - 1], mZoom))
            Constants.FOLLOW_STARTING_POINT -> mGoogleMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(mPoints!![0], mZoom))
            else                            -> mGoogleMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(mPoints!![mPoints!!.size - 1], mZoom))
        }
        /*The graphic elements of the map display have now all been configured, so set the
         *mPrepared flag so that succeeding calls to onLoadFinished will merely update them as
         *new location data comes in and not redo this initial setup.
         */
        mPrepared = true
    }

    /*Set up tracking depending up mViewMode. SHOW_ENTIRE_ROUTE is the default, but mViewMode may
    * be set to another value when the map is recreated. This method is also used when the user
    * affirmatively selects a different View Mode in the OptionsMenu.
    */
    private fun setTrackingMode() {
        if (mPoints!!.size == 0) {
            Log.i(TAG, "mPoints.size() is zero - bailing out of setTrackingMode")
            return
        }
        val latLng: LatLng

        when (mViewMode) {
            Constants.FOLLOW_END_POINT      -> {
                /*Fix the camera on the last location in the run at the zoom level that was last used
                 *for this or the FOLLOW_START_POINT view mode. Also make sure the End Marker is
                 *placed at the same location so that the End Marker stays in sync with mLastLocation..
                 *Move the camera to the end point at the specified zoom level, animating the
                 *movement over three seconds.
                 */
                latLng = mPoints!![mPoints!!.size - 1]
                mGoogleMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, mZoom), 3000, null)
                //Update the position of the end marker
                mEndMarker!!.position = latLng
            }
            Constants.FOLLOW_STARTING_POINT -> {
                /*//Fix the camera on the first location in the run at the designated zoom level.
                 *The start marker never moves, so there's never any need to update it.
                 */
                latLng = mPoints!![0]
                mGoogleMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, mZoom), 3000, null)
            }
            else                            ->
                /*This handles both SHOW_ENTIRE_ROUTE and NO_UPDATES. Set the camera at the center
                 *of the area defined by mBounds, again leaving a buffer zone at the edges of the
                 *screen set according to screen size. The map has already been created, so we don't
                 *need to tell the CameraUpdate about the  map's size. The zoom level is set
                 *according to the size of the area that needs to be displayed. We don't save the
                 *zoom level here, so that any switch to FOLLOW_END_POINT or FOLLOW_START_POINT mode
                 *will use the zoom level that was last used for one of those modes.
                 */
                mGoogleMap!!.animateCamera(CameraUpdateFactory.newLatLngBounds(mBounds, mPadding), 3000, null)
        }
    }

    /*Rather than set a fixed amount of padding for the map image, calculate an appropriate
     *amount based upon the size of the display in its current orientation
     */
    private fun calculatePadding(): Int {
        val width = context!!.resources.displayMetrics.widthPixels
        val height = context!!.resources.displayMetrics.heightPixels
        val minMetric = Math.min(width, height)
        /*The figure of .075 was arrived at through trial and error on my Pixel XL and Nexus 7
         *(2013). Is there a good way to calculate an appropriate value that will work for any
         *device?
         */
        return (minMetric * .075).toInt()
    }

    private fun checkPermission(): Boolean {
        val permissionState = ContextCompat.checkSelfPermission(context!!,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                Constants.REQUEST_LOCATION_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == Constants.REQUEST_LOCATION_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(activity, "Location permissions granted.",
                        Toast.LENGTH_LONG).show()
                mService!!.startLocationUpdates(mRunId)
                mRunManager!!.startTrackingRun(mRunId)
                /*Tracking a Run disables the New Run menu item, so we have to invalidate the
                 *Options Menu to update its appearance.
                 */
                activity!!.invalidateOptionsMenu()
                updateUI()
            } else {
                Toast.makeText(activity, "Location permissions denied. No tracking possible.",
                        Toast.LENGTH_LONG).show()
                if (mRun!!.duration == 0L) {
                    Toast.makeText(activity, "Never got any locations for this Run; deleting.",
                            Toast.LENGTH_LONG).show()
                    RunManager.deleteRun(mRunId)
                }
            }
        } else {
            Log.i(TAG, "REQUEST_LOCATION_PERMISSIONS is the only requestCode used. How'd you get " + "here!?!")
        }
    }

    private fun checkLocationSettings() {
        val result = LocationServices.getSettingsClient(context!!).checkLocationSettings(sLocationSettingsRequest)
        result.addOnCompleteListener { task ->
            try {
                task.getResult(ApiException::class.java)
                RunTracker2Kotlin.locationSettingsState = true
                if (!checkPermission()) {
                    requestPermission()
                } else {
                    mService!!.startLocationUpdates(mRunId)
                }
            } catch (apie: ApiException) {
                when (apie.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        val resolvable = apie as ResolvableApiException
                        resolvable.startResolutionForResult(activity,
                                Constants.LOCATION_SETTINGS_CHECK)
                    } catch (sie: android.content.IntentSender.SendIntentException) {
                        Toast.makeText(RunTracker2Kotlin.instance,
                                "SendIntentException caught when trying startResolutionResult for LocationSettings",
                                Toast.LENGTH_LONG).show()
                    } catch (cce: ClassCastException) {
                        Log.e(TAG, "How'd you get a ClassCastException?")
                    }

                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        RunTracker2Kotlin.locationSettingsState = false
                        Toast.makeText(RunTracker2Kotlin.instance,
                                "Location settings are inadequate and cannot be fixed here. \n" + "Dialog not created.",
                                Toast.LENGTH_LONG)
                                .show()
                    }
                }
            }
        }
    }

    private inner class RunCursorLoaderCallbacks : LoaderManager.LoaderCallbacks<Cursor> {

        override fun onCreateLoader(d: Int, args: Bundle?): Loader<Cursor> {
            val runId = args?.getLong(Constants.ARG_RUN_ID)
            //Return a Loader pointing to a cursor containing a Run with specified RunId
            return RunCursorLoader(context!!, runId)
        }

        override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
            Log.i(TAG, "Called  onLoadFinished() for Run Loader")
            if (cursor == null) {
                Log.w(TAG, "Cursor returned by RunCursorLoader was null")
                return
            } else if (!cursor.moveToFirst()) {
                Log.i(TAG, "Couldn't move RunCursor to First Position in onLoadFinished")
                return
            } else if (cursor.isAfterLast) {
                Log.i(TAG, "In RunCursorLoaderCallbacks, cursor went past last position")
                return
            }

            val run = RunDatabaseHelper.getRun(cursor)
            Log.i(TAG, "Run returned by RunCursor null?" + (run  == null))
            Log.i(TAG, "Run returned b RunCursor is #${run?.id}")

            if (run == null) {
                Log.i(TAG, "Run returned by RunCursor was null: " + mRunId)
                return
            }

            mRun = run
            mRunId = run.id
            //Now that we know the RunLoader has given us a valid Run, update the UI.
            updateUI()
        }

        override fun onLoaderReset(loader: Loader<Cursor>) {
            //do nothing
        }
    }

    private inner class LocationListCursorCallbacks : LoaderManager.LoaderCallbacks<Cursor> {
        /*Return a loader with a cursor containing all the locations recorded for the designated
         *Run, including the most recently added one. We need the cursor to contain all the
         *Locations so that we can rebuild the map when the Activity is destroyed and rebuilt.
         */
        override fun onCreateLoader(d: Int, args: Bundle?): Loader<Cursor> {
            val runId = args?.getLong(Constants.ARG_RUN_ID)
            return MyLocationListCursorLoader(context!!, runId)
        }

        override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
            Log.i(TAG, "Called onLoadFinished")
            //Extract the latest location added to the location list and update the UI accordingly
            if (cursor != null && !cursor.isClosed) {
                mLocationCursor = cursor
                if (mLocationCursor!!.moveToLast()) {
                    mLastLocation = getLocation(mLocationCursor!!)
                    if (mLastLocation != null) {
                        Log.i(TAG, "mLastLocation returned in cursor is " + mLastLocation.toString())
                        Log.i(TAG, "mLastLocation timestamp is "  + mLastLocation?.time)
                        if (mLocationCursor!!.moveToPrevious()) {
                            mPreviousLocation = RunDatabaseHelper.getLocation(mLocationCursor!!)
                        }
                        updateUI()
                        Log.i(TAG, "Called updateUI() after getting new mLastLocation from LocationListLoader")
                    } else {
                        Log.i(TAG, "MyLocationListCursor Loader for " + mRunId +
                                " returned a null Location")
                    }

                } else {
                    Log.i(TAG, "In MyLocationListCursorLoader for Run " + mRunId +
                            ", couldn't move to first position of cursor.")
                }
            } else {
                Log.i(TAG, "MyLocationListCursorLoader for Run " + mRunId +
                        " returned a null cursor")
            }
            updateUI()
        }

        override fun onLoaderReset(loader: Loader<Cursor>) {
            //do nothing
        }
    }

    /*Simple AsyncTask to load the locations for this Run stored in RunManager's singletons into a
     *LatLngBounds and a List<LatLng> for the display of the MapView for this RunId to speed
     *re-creation of the map. Not currently implemented, so commented out.
     */
    /*private static class LoadPointsAndBounds extends AsyncTask<Void, Void, Void> {
        private final Cursor mCursor;
        private final int mLocationCount;
        private final ArrayList<LatLng> mPoints;
        private LatLngBounds mBounds;
        final LatLngBounds.Builder mBuilder;

        LoadPointsAndBounds(Cursor cursor, int locationCount, ArrayList<LatLng> points,
                            LatLngBounds bounds, LatLngBounds.Builder builder){
            mCursor = cursor;
            mLocationCount = locationCount;
            mPoints = points;
            mBounds = bounds;
            mBuilder = builder;
        }
        @Override
        protected Void doInBackground(Void... params) {
            Location location;
            LatLng latLng;
            //If mPoints and mBounds already have some locations loaded into them, just load the
            //locations in the cursor that haven't yet been loaded into them.
            if (mLocationCount > 0 && mPoints.size() > 0 && mCursor.getCount() > mLocationCount && mBounds != null) {
                //Log.i(TAG, "In LoadPointsAndBounds for Run " + RunManager.getCurrentRunId() + ", mPoints.size() is " +
                //mPoints.size() + "and mBounds was not null.");
                if(mCursor.moveToPosition(mLocationCount)) {
                    while (!mCursor.isAfterLast()) {
                        location = getLocation(mCursor);
                        try {
                            latLng = new LatLng(location != null ? location.getLatitude() : 0, location != null ? location.getLongitude() : 0);
                            mPoints.add(latLng);
                            mBounds = mBounds.including(latLng);
                        } catch (NullPointerException npe) {
                            Log.e(TAG, "Caught an NPE trying to extract a LatLng from a location in a cursor");
                        }
                        mCursor.moveToNext();
                    }
                }
            } else {
                //If mPoints has no members, mBounds should be null - start from the first location
                //in the cursor and load all location data into mPoints and a Builder for mBounds.
                mCursor.moveToFirst();
                while (!mCursor.isAfterLast()) {
                    location = getLocation(mCursor);
                    try {
                        latLng = new LatLng(location != null ? location.getLatitude() : 0, location != null ? location.getLongitude() : 0);
                        mPoints.add(latLng);
                        mBuilder.include(latLng);
                    } catch (NullPointerException npe){
                        Log.e(TAG, "Caught an NPE while extracting a LastLng from a location in a cursor");
                    }
                    mCursor.moveToNext();
                }
                //If we got any locations from the cursor, we need to build mBounds.
                if (mPoints.size() > 0) {
                    mBounds = mBuilder.build();
                }
            }
            mCursor.close();
            return null;
        }
    }*/

    /*A Broadcast Receiver used to display results of database actions and to receive signals that
     *the UI needs to be refreshed because of actions taken in other CombinedFragments.
     */
    private inner class ResultsReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            val action = intent.action ?: return
            when (action) {
                Constants.ACTION_REFRESH_UNITS -> {
                    updateUI()
                    return
                }
                Constants.SEND_RESULT_ACTION   -> {
                    /*Dispatch Intents for processing based upon the value passed in the
                     *ACTION_ATTEMPTED Extras key. Data specific to each different ACTION_ATTEMPTED
                     *value is carried with the EXTENDED_RESULTS_DATA key
                     */
                    val actionAttempted = intent.getStringExtra(Constants.ACTION_ATTEMPTED)
                    val runId = intent.getLongExtra(Constants.ARG_RUN_ID, -1)
                    when (actionAttempted) {
                        Constants.ACTION_UPDATE_START_DATE         -> {
                            val result = intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1)
                            val toastTextRes: Int
                            /*The getWritableDatabase.update() method returns the number of rows
                             *affected; that value is returned to the updateStartDateTask and passed
                             *on to here in a broadcast intent. If no rows were affected or more
                             *than one row was affected, something went wrong! The updateStartDateTask
                             *no longer reports successful updates, so the "result == 1" branch in
                             *the if-else chain below will never be executed.
                             */
                            toastTextRes = when  {
                                result == 1 -> R.string.update_run_start_date_success
                                result == 0 -> R.string.update_run_start_date_failed
                                result > 1-> R.string.multiple_runs_dates_updated
                                else -> R.string.unknown_start_date_error
                            }
                            /*if (result == 1) {
                                toastTextRes = R.string.update_run_start_date_success
                            } else if (result == 0) {
                                toastTextRes = R.string.update_run_start_date_failed
                            } else if (result > 1) {
                                toastTextRes = R.string.multiple_runs_dates_updated
                            } else {
                                toastTextRes = R.string.unknown_start_date_error
                            }*/
                            /*If an error occurred, put up a Toast advising the user of how things
                             *went wrong. Otherwise, do nothing.
                             */
                            if (result != 1) {
                                if (isAdded) {
                                    Toast.makeText(activity,
                                            toastTextRes,
                                            Toast.LENGTH_LONG)
                                            .show()
                                }
                            }
                            updateUI()
                        }
                        Constants.ACTION_UPDATE_START_ADDRESS      -> {
                            val result = intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1)
                            val toastTextRes: Int
                            /*The getWritableDatabase.update() method returns the number of rows
                             *affected; that value is returned to the updateStartDateTask and passed
                             *on to here in a broadcast intent. If no rows were affected or more
                             *than one row was affected, something went wrong! The updateStartDateTask
                             *no longer reports successful updates, so the "result == 1" branch in
                             *the if-else chain below will never be executed.
                             */
                            toastTextRes = when {
                                result == 1 -> R.string.update_run_start_address_success
                                result == 0 -> R.string.update_run_start_address_failed
                                result > 1 -> R.string.multiple_start_addresses_error
                                else -> R.string.unknown_start_address_error
                            }
                            /*if (result == 1) {
                                toastTextRes = R.string.update_run_start_address_success
                            } else if (result == 0) {
                                toastTextRes = R.string.update_run_start_address_failed
                            } else if (result > 1) {
                                toastTextRes = R.string.multiple_start_addresses_error
                            } else {
                                toastTextRes = R.string.unknown_start_address_error
                            }*/
                            /*If an error occurred, put up a Toast advising the user of how things
                             *went wrong. Otherwise, do nothing.
                             */
                            if (result != 1) {
                                if (isAdded) {
                                    Toast.makeText(activity,
                                                    toastTextRes,
                                                    Toast.LENGTH_LONG).show()
                                }
                            }
                            updateUI()
                        }
                        Constants.ACTION_UPDATE_END_ADDRESS        -> {
                            val result = intent.getIntExtra(Constants.EXTENDED_RESULTS_DATA, -1)
                            val toastTextRes: Int
                            /*The getWritableDatabase.update() method returns the number of rows
                             *affected; that value is returned to the updateStartDateTask and passed
                             *on to here in a broadcast intent. If no rows were affected or more
                             *than one row was affected, something went wrong! The updateStartDateTask
                             *no longer reports successful updates, so the "result == 1" branch in
                             *the if-else chain below will never be executed.
                             */
                            /*if (result == 1) {
                                toastTextRes = R.string.update_run_end_address_success
                            } else if (result == 0) {
                                toastTextRes = R.string.update_end_address_failed
                            } else if (result > 1) {
                                toastTextRes = R.string.multiple_runs_end_addresses_updated
                            } else {
                                toastTextRes = R.string.unknown_end_address_update_error
                            }*/
                            toastTextRes = when {
                                result == 1 -> R.string.update_run_end_address_success
                                result == 0 -> R.string.update_end_address_failed
                                result > 1 -> R.string.multiple_runs_end_addresses_updated
                                else -> R.string.unknown_end_address_update_error
                            }
                            /*If an error occurred, put up a Toast advising the user of how things
                             *went wrong. Otherwise, do nothing.
                             */
                            if (result != 1) {
                                if (isAdded) {
                                    Toast.makeText(activity, toastTextRes, Toast.LENGTH_LONG).show()
                                }
                            }
                            updateUI()
                        }
                        Constants.ACTION_INSERT_LOCATION           -> {
                            /*Report any failure while inserting a new location. Successful
                             *insertions are not reported.
                             */
                            val resultsString = intent.getStringExtra(Constants.EXTENDED_RESULTS_DATA)
                            val shouldStop = intent.getBooleanExtra(Constants.SHOULD_STOP, false)
                            Toast.makeText(getContext(), resultsString, Toast.LENGTH_LONG).show()
                            if (shouldStop) {
                                mService!!.stopLocationUpdates()
                            }
                            /*If mViewMode or zoom level is changed for any CombinedFragment, a
                             *broadcast is sent to the one or two other CombinedFragments currently
                             *active in the ViewPager so they will also change to the same view mode
                             *and zoom level
                             */
                            if (runId == -1L || runId == mRunId) {
                                Log.i(TAG, "mRunId in ACTION_REFRESH_MAPS is $mRunId; runId is $runId")
                                Log.i(TAG, "Same or bad runId in ACTION_REFRESH_MAPS - bailing")
                                return
                            }
                            mViewMode = RunTracker2Kotlin.prefs!!.getInt(Constants.TRACKING_MODE,
                                    Constants.SHOW_ENTIRE_ROUTE)
                            mZoom = RunTracker2Kotlin.prefs!!.getFloat(Constants.ZOOM_LEVEL, 17.0f)
                            setTrackingMode()
                            updateUI()
                        }

                        Constants.ACTION_REFRESH_MAPS              -> {
                            if (runId == -1L || runId == mRunId) {
                                Log.i(TAG, "mRunId in ACTION_REFRESH_MAPS is $mRunId; runId is $runId")
                                Log.i(TAG, "Same or bad runId in ACTION_REFRESH_MAPS - bailing")
                                return
                            }
                            mViewMode = RunTracker2Kotlin.prefs!!.getInt(Constants.TRACKING_MODE, Constants.SHOW_ENTIRE_ROUTE)
                            mZoom = RunTracker2Kotlin.prefs!!.getFloat(Constants.ZOOM_LEVEL, 17.0f)
                            setTrackingMode()
                            updateUI()
                        }

                        Constants.ACTION_STOP_UPDATING_END_ADDRESS ->
                            /*Update UI to enable Start Button and disable Stop Button when tracking
                             *is stopped for exceeding 100 meter Run continuation limit.
                             */
                            updateUI()

                        else                                       -> Log.i(TAG, "How'd you get here!?! Not a defined ACTION!")
                    }
                }
            }

        }
    }

    companion object {
        private val TAG = "CombinedFragment"
        private val sLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
        private var sLocationSettingsRequest: LocationSettingsRequest? = null

        fun newInstance(runId: Long): CombinedFragment {
            val args = Bundle()
            args.putLong(Constants.ARG_RUN_ID, runId)
            val cf = CombinedFragment()
            cf.arguments = args
            return cf
        }

        private val locationSettingsRequest: LocationSettingsRequest
            get() = LocationSettingsRequest.Builder()
                    .addLocationRequest(sLocationRequest)
                    .build()
    }
}
