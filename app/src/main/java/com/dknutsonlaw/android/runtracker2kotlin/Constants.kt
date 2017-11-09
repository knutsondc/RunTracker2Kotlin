package com.dknutsonlaw.android.runtracker2kotlin

import android.net.Uri

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Created by dck on 11/4/15.
 *
 * Constant values used throughout the program, arranged alphabetically.
 */
internal object Constants {
    val DATE_FORMAT = SimpleDateFormat("E, MMM dd, yyyy, hh:mm:ss a z", Locale.US)
    //Value for use of Imperial system for distance and altitude measurements
    val IMPERIAL = true
    //Value for use of Metric system for distance and altitude measurements
    val METRIC = false
    //Conversion factors to convert meters (the unit by which the Android Location API calculates
    //distances) into the units we wish to display to the user
    val METERS_TO_FEET = 3.28083989501
    val METERS_TO_MILES = .000621371192237

    //MapView Menu selection items
    val SHOW_ENTIRE_ROUTE = 0
    val FOLLOW_END_POINT = 1
    val FOLLOW_STARTING_POINT = 2
    val NO_UPDATES = 3
    //Sort direction upon restarting a RunRecyclerListFragment or RunPagerActivity
    val KEEP_EXISTING_SORT = -1
    //Identifiers for the two different types of loaders we use in RunFragment
    val LOAD_LOCATION = 1
    val LOAD_RUN = 0
    val LOCATION_SETTINGS_CHECK = 8000
    val MESSAGE_PLAY_SERVICES_RESOLUTION_REQUEST = 8001

    //Labels for Messages between UI Fragments and BackgroundLocationService

    val NOTIFICATION_ID = 1
    //Label for BackgroundLocationService to tell the UI Fragment that Location Permissions needed
    val REQUEST_LOCATION_PERMISSIONS = 1
    //Values to pass back from DeleteRunsDialog so that correct Fragment will act on the result
    val RUN_LIST_RECYCLER_FRAGMENT = 0
    val COMBINED_FRAGMENT = 1
    //Label for loader used in RunRecyclerListFragment
    val RUN_LIST_LOADER = 0
    //Sort order values used in RunRecyclerListFragment and RunPagerActivity
    val SORT_BY_DATE_ASC = 0
    val SORT_BY_DATE_DESC = 1
    val SORT_BY_DISTANCE_ASC = 2
    val SORT_BY_DISTANCE_DESC = 3
    val SORT_BY_DURATION_ASC = 4
    val SORT_BY_DURATION_DESC = 5
    val SORT_NO_RUNS = 6

    val RUN_LIST = 0
    val SINGLE_RUN = 1
    val LOCATION_LIST = 2
    val SINGLE_LOCATION = 3
    //Identifier of version of db schema
    val VERSION = 1

    /*//Values preventing insertion of locations that are too far away from the last previous location
     *to be considered a "continuation" of a run.
     */
    val CONTINUATION_DISTANCE_LIMIT: Long = 100 //100 meters
    val CONTINUATION_TIME_LIMIT = (60 * 1000).toLong() //60 seconds

    /*Labels used in creating Intents used to invoke TrackingLocationIntentService and to report the
    *results of the operations to UI elements.
    */
    val ACTION_ATTEMPTED = "com.dknutsonlaw.android.runtracker.action.attempted"
    val ACTION_DELETE_RUN = "com.dknutsonlaw.android.runtracker.action.delete.run"
    val ACTION_DELETE_RUNS = "com.dknutsonlaw.android.runtracker.action.delete.runs"
    val ACTION_INSERT_LOCATION = "com.dknutsonlaw.android.runtracker.action.insert.location"
    val ACTION_INSERT_RUN = "com.dknutsonlaw.android.runtracker.action.insert.run"
    val ACTION_LOCATION = "com.dknutsonlaw.android.runtracker2kotlin.ACTION_LOCATION"
    val ACTION_REFRESH_MAPS = "com.dknutsonlaw.android.runtracker2kotlin.action.refresh.maps"
    val ACTION_REFRESH_UNITS = "com.dknutsonlaw.android.runtracker2kotlin.action.refresh.units"
    val ACTION_START_UPDATING_END_ADDRESS = "com.dknutsonlaw.android.runtracker2kotlin.action.start.updating.end.address"
    val ACTION_STOP_UPDATING_END_ADDRESS = "com.dknutsonlaw.android.runtracker2kotlin.action.stop.updating.end.address"
    val ACTION_UPDATE_END_ADDRESS = "com.dknutsonlaw.android.runtracker.action.update.end.address"
    val ACTION_UPDATE_START_ADDRESS = "com.dknutsonlaw.android.runtracker2kotlin.action.update.start.address"
    val ACTION_UPDATE_START_DATE = "com.dknutsonlaw.android.runtracker.action.update.start.date"
    //Label for saving adapter item count to SharedPreferences and in savedInstanceState Bundles.
    val ADAPTER_ITEM_COUNT = "com.dknutsonlaw.android.runtracker2kotlin.adapter.item.count"
    //Label for saving adapter position of Run currently being displayed in RunPagerActivity.
    val ADAPTER_POSITION = "com.dknutsonlaw.com.android.runtracker2kotlin.adapter.position"
    //Label used to pass ID of run to use in a new instance of RunFragment.
    val ARG_RUN_ID = "RUN_ID"
    //Labels for columns in the Location table.
    val COLUMN_LOCATION_ALTITUDE = "altitude"
    val COLUMN_LOCATION_LATITUDE = "latitude"
    val COLUMN_LOCATION_LONGITUDE = "longitude"
    val COLUMN_LOCATION_PROVIDER = "provider"
    val COLUMN_LOCATION_RUN_ID = "run_id"
    val COLUMN_LOCATION_TIMESTAMP = "timestamp"
    //Labels for column in the Run table.
    val COLUMN_RUN_DISTANCE = "distance"
    val COLUMN_RUN_DURATION = "duration"
    val COLUMN_RUN_END_ADDRESS = "end_address"
    val COLUMN_RUN_ID = "_id"
    val COLUMN_RUN_START_ADDRESS = "start_address"
    val COLUMN_RUN_START_DATE = "start_date"
    val CURRENTLY_VIEWED_RUN = "currently_viewed_run"
    //Label for name of database.
    val DB_NAME = "runs.sqlite"
    //Label used to pass along extra info about results of database operations.
    val EXTENDED_RESULTS_DATA = "com.dknutsonlaw.android.runtracker.extended.results.data"

    val EXTRA_ERROR_CODE = "com.dknutsonlaw.android.runtracker.error_code"
    //Label used to pass along run IDs in Intents
    val EXTRA_RUN_ID = "com.dknutsonlaw.android.runtracker.run_id"
    /*Label used to pass along the existing sort order from RunRecyclerListFragment to
     *RunPagerActivity and vice-versa.
     */
    val EXTRA_SORT_ORDER = "com.dknutsonlaw.android.runtracker2kotlin.sort_order"
    val EXTRA_VIEW_HASHMAP = "com.dknutsonlaw.android.runtracker2kotlin.view_hash_map"
    //Label to identify type of fragment that called DeleteRunsDialog
    val FRAGMENT = "com.dknutsonlaw.android.runtracker2kotlin.fragment"
    //Label for placing number of locations deleted in a broadcast intent reporting results
    val LOCATIONS_DELETED = "com.dknutsonlaw.android.runtracker2kotlin.locations.deleted"
    //Label for distance/altitude measurement system
    val MEASUREMENT_SYSTEM = "com.dknutsonlaw.android.runtracker2kotlin.measurement_system"
    //Label for number of Runs to delete argument to pass to DeleteRunsDialog
    val NUMBER_OF_RUNS = "com.dknutsonlaw.android.runtracker2kotlin.number_of_runs"
    //Label used to communicate a location parameter in an Intent or a Bundle
    val PARAM_LOCATION = "com.dknutsonlaw.android.runtracker.param.location"
    //Label used to communicate a run parameter in an Intent or a Bundle
    val PARAM_RUN = "com.dknutsonlaw.android.runtracker.param.run"
    val PARAM_RUN_IDS = "com.dknutsonlaw.android.runtracker.param.runids"
    //Label used to identify the current run id into SystemPreferences
    val PREF_CURRENT_RUN_ID = "prefs.currentRunId"
    //Label used to identify file used in SystemPreferences operations
    val PREFS_FILE = "runs"
    val PRIMARY_CHANNEL = "primary.channel"
    val RUNS_DELETED = "coma.dknutsonlaw.android.runtracker2kotlin.runs.deleted"
    //Label used to identify run id retrieved from SystemPreferences
    val SAVED_RUN_ID = "com.dknutsonlaw.android.runtracker2kotlin.saved_run_id"
    //Label to store boolean in shared preferences for whether a mapview should be scrollable
    val SCROLLABLE = "com.dknutsonlaw.android.runtracker2kotlin.scrollable"
    val SCROLL_ON = "com.dknutsonlaw.android.runtracker2kotlin.scroll_on"
    //Label used to communicate results of TrackingLocationIntentService operations in response Intents
    val SEND_RESULT_ACTION = "com.dknutsonlaw.android.runtracker.send.result.action"
    val SHOULD_STOP = "com.dknutsonlaw.android.runtracker2kotlin.should.stop"
    //Label used to store and retrieve the run sort order in Intents and Bundles
    val SORT_ORDER = "sort"
    //Label used to store and retrieve fragment subtitles in Intents and Bundles
    val SUBTITLE = "subtitle"
    //Labels for the two data tables in the database
    val TABLE_LOCATION = "location"
    val TABLE_RUN = "run"
    val TRACKING_MODE = "tracking_mode"
    val UPDATED_ADDRESS_RESULT = "addressResult"
    val ZOOM_LEVEL = "zoom_level"

    /*Labels for the Uris used for observing and reporting changes in the database tables
     *triggering appropriate actions in loaders and in forming query and update requests directed
     *to the database
     */
    val AUTHORITY = "com.dknutsonlaw.android.runtracker2kotlin"
    private val CONTENT_URI = Uri.parse("content://" + AUTHORITY)
    val URI_TABLE_LOCATION = Uri.withAppendedPath(CONTENT_URI, "location")!!
    val URI_TABLE_RUN = Uri.withAppendedPath(CONTENT_URI, "run")!!
}
