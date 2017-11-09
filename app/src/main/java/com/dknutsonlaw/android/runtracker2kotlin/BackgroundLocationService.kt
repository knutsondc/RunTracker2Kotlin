package com.dknutsonlaw.android.runtracker2kotlin

/*
 * A Service intended to allow the user to start and stop location updates and guarantee that the
 * updates will continue without regard to the state of the UI elements of the program. The service
 * is bound while UI elements are active but becomes a started foreground service when all UI
 * elements have unbound to make sure location updates continue without interruption.
 */

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
//import android.content.pm.PackageManager;
import android.graphics.Color
import android.os.Binder
import android.os.Build
//import android.os.Bundle;
//import android.os.Handler;
import android.os.IBinder
//import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.Toast
import com.dknutsonlaw.android.runtracker2kotlin.ui.RunPagerActivity

//import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnFailureListener
//import com.google.android.gms.location.LocationSettingsRequest;
//import com.google.android.gms.location.LocationSettingsResult;
//import com.google.android.gms.location.LocationSettingsStatusCodes;

//import java.lang.ref.WeakReference;
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ScheduledThreadPoolExecutor//import android.os.Message;

//import android.os.Messenger;
//import android.os.RemoteException;


class BackgroundLocationService : Service() {

    private val mBinder = LocationBinder()
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var mLocationRequest: LocationRequest? = null
    private var mBound = false

    internal inner class LocationBinder : Binder() {
        val service: BackgroundLocationService
            get() = this@BackgroundLocationService
    }

    override fun onCreate() {
        Log.i(TAG, "Reached onCreate()")
        super.onCreate()
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        /*We need a LocationRequest to set the parameters for the location updates we want Location
         *Services to send us.
         */
        mLocationRequest = buildLocationRequest()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "Reached onStartCommand - calling startForeground")
        startForeground(Constants.NOTIFICATION_ID, createNotification(this))
        /*This Service is started only when we're tracking a Run and the CombinedRun fragment
         *for that Run unbinds and goes into the background, so to keep the Service going at
         *full speed, it needs to be started and run as a foreground service. The calls to
         *startService() and startForeground() are in onUnBind(); if the CombinedFragment has
         *already been rebound to the Service, then we don't need to be a foreground service*/
        if (mBound) {
            stopForeground(true)
        }
        return Service.START_NOT_STICKY
    }

    private fun buildLocationRequest(): LocationRequest {
        Log.i(TAG, "Now creating LocationRequest.")
        //Construct the Location Request
        val locationRequest = LocationRequest()
        //Use high accuracy
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = 1000
        //locationRequest.setSmallestDisplacement(2.5f);
        return locationRequest
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.i(TAG, "In onBind()")
        try {
            /*When bound to a CombinedRun Fragment, the RunPagerActivity, or the RunRecyclerFragment
             *the Service need no longer run as a foreground service.
             */
            stopForeground(true)
            mBound = true
        } catch (npe: NullPointerException) {
            Log.e(TAG, " Caught a null pointer trying to stopForeground")
        }

        return mBinder
    }

    override fun onRebind(intent: Intent) {
        Log.i(TAG, "In onRebind()")
        /*When bound to a CombinedRun Fragment, the Service need no longer run as a foreground
         *service.
         */
        try {
            stopForeground(true)
            mBound = true
        } catch (npe: NullPointerException) {
            Log.e(TAG, "Caught a null pointer trying to stopForeground")
        }

        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(TAG, "In onUnbind")
        /*When the last UI element unbinds, the Service needs to run as a foreground service, so we
         *call startForeground(). We call startService or startForeGroundService in order to get a
         *Notification for as long as all UI elements are in the background because startForegound
         *generates a Notification only when the service was started through onStartCommand.
         */
        if (RunManager.isTrackingRun) {
            Log.i(TAG, "Starting service in onUnbind() method.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, BackgroundLocationService::class.java))
            } else {
                startService(Intent(this, BackgroundLocationService::class.java))
            }
            mBound = false
        } else {
            Log.i(TAG, "Service is unbound, but not starting service - not tracking any Run")
        }
        return true
    }

    internal fun startLocationUpdates(runId: Long) {
        /*Location updates are given to the PendingIntent and broadcast with the Action
         *ACTION_LOCATION. TrackingLocationReceiver, which is statically declared in
         *AndroidManifest, receives those broadcasts and calls RunManager's insertLocation() method
         *with the Location object in the PendingIntent.
         */
        Log.i(TAG, "Reached startLocationUpdates() for Run " + runId)

        /*Calling the getPendingIntent() method from RunManager each time we start or stop location
         *updates instead of assigning the PendingIntent to an instance variable seems to be required
         *to make sure that we use the same PendingIntent and thereby successfully stop location
         *updates.
         */
        try {
            mFusedLocationClient!!.requestLocationUpdates(
                    mLocationRequest,
                    RunManager.getLocationPendingIntent(this, true)
            )
                    .addOnFailureListener(object : OnFailureListener {
                        internal var failureMessage: String? = null
                        override fun onFailure(e: Exception) {
                            failureMessage = when(e) {
                                is ApiException -> e.statusMessage
                                else -> e.message
                            }
                            /*if (e is ApiException) {
                                failureMessage = e.statusMessage
                            } else {
                                failureMessage = e.message
                            }*/
                            Log.e(TAG, "requestLocationUpdates() for Run $runId failed: $failureMessage")
                        }
                    })
                    .addOnCompleteListener { task ->
                        Log.i(TAG, task.toString())
                        if (task.isSuccessful) {
                            Log.i(TAG, "Completed request for Location updates for Run " + runId)
                        } else {
                            val e = task.exception
                            if (e != null) {
                                Log.e(TAG, "Exception is: " + e.message)
                            } else {
                                Log.e(TAG, "Exception was null!")
                            }
                        }
                    }
                    .addOnSuccessListener { _ ->
                        /*Now that the request for location updates has succeeded, tell RunManager to
                 *start the task that periodically updates EndingAddress.
                 */
                        val updatesStarted = Intent(Constants.ACTION_START_UPDATING_END_ADDRESS)
                        val localBroadcastManager = LocalBroadcastManager.getInstance(this@BackgroundLocationService)
                        localBroadcastManager.sendBroadcast(updatesStarted)
                    }
            /*We should never catch an exception here because we've just gotten through checking
         *permissions.
         */
        } catch (e: SecurityException) {
            Log.i(TAG, "SecurityException thrown when trying to start location updates:" + e.toString())
        }

    }

    internal fun stopLocationUpdates() {

        Log.i(TAG, "Removing Location updates.")

        try {
            /*Calling the getPendingIntent() method from RunManager each time we start or stop
             *location updates instead of assigning the PendingIntent to an instance variable seems
             *to be required to make sure that we use the same PendingIntent and thereby
             *successfully stop location updates.
             */
            mFusedLocationClient!!.removeLocationUpdates(RunManager.getLocationPendingIntent(this, true))
                    .addOnSuccessListener { _ ->
                        Log.i(TAG, "Successfully stopped location updates.")
                        /*Cancel the PendingIntent because we no longer need it to broadcast
                         *location updates and calling RunManager.getPendingIntent(context, false)
                         *will return null to indicate that no Runs are being tracked.
                         */
                        if (RunManager.getLocationPendingIntent(
                                this@BackgroundLocationService,
                                false) != null) {
                            RunManager.getLocationPendingIntent(
                                    this@BackgroundLocationService,
                                    false)!!.cancel()
                        }
                        Log.d(TAG, "We canceled the PendingIntent. Is it null? " + (RunManager.getLocationPendingIntent(
                                this@BackgroundLocationService,
                                false) == null))
                        val updatesStopped = Intent(
                                Constants.ACTION_STOP_UPDATING_END_ADDRESS)
                        val localBroadcastManager = LocalBroadcastManager.getInstance(this@BackgroundLocationService)
                        localBroadcastManager.sendBroadcast(updatesStopped)
                        stopForeground(true)
                        RunTracker2Kotlin.prefs!!.edit().remove(Constants.PREF_CURRENT_RUN_ID).apply()
                        stopSelf()
                    }
                    .addOnFailureListener { _ ->
                        Log.d(TAG, "Couldn't stop location updates!")
                        Toast.makeText(this@BackgroundLocationService,
                                "Couldn't stop location updates!!", Toast.LENGTH_LONG).show()
                        stopSelf()
                    }
        } catch (se: SecurityException) {
            Log.e(TAG, "Apparently lost location permission. Can't remove updates.")
        }

    }

    override fun onDestroy() {

        val shutdownList = sStpe.shutdownNow()
        Log.i(TAG, "There were " + shutdownList.size +
                " tasks queued when BackgroundLocationService was destroyed.")
        Log.i(TAG, DateFormat.getDateTimeInstance().format(Date()) + ": Stopped")
        super.onDestroy()
    }

    companion object {

        private val TAG = "location.service"
        //Executor service to run database operations and end address updates on separate non-UI threads.
        private val sStpe = ScheduledThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors() + 1)
        internal val notification = createNotification(RunTracker2Kotlin.instance)

        /*When the user tracks this Run and then puts all UI elements in the background, create a
     *Notification that will stay around until the a UI element returns to the foreground, When
     *clicked, it will send the user back to the Run being tracked.
     */

        internal fun createNotification(context: Context?): Notification {
            /*Create an explicit Intent for RunPagerActivity - we'll tie this to the run that's being
         *monitored
         */
            val runPagerActivityIntent = RunPagerActivity.newIntent(context!!,
                    Constants.KEEP_EXISTING_SORT, RunManager.currentRunId)
            /*The stack builder object will contain an artificial back stack for RunPagerActivity so
         *that when navigating back from the CombinedFragment we're viewing we'll go to the
         *RunRecyclerListFragment instead of the Home screen.
         */
            val stackBuilder = TaskStackBuilder.create(context)
                    /*The artificial stack consists of everything defined in the manifest as a
                         *parent or parent of a parent of the Activity to which the Notification
                         *will return us when we click on it.
                         */
                    .addNextIntentWithParentStack(runPagerActivityIntent)
            val resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //Oreo requires a NotificationChannel. Everything older does not.
                val channel = NotificationChannel(Constants.PRIMARY_CHANNEL,
                        context.getString(R.string.default_channel), NotificationManager.IMPORTANCE_DEFAULT)
                channel.lightColor = Color.GREEN
                channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                channel.enableLights(true)
                channel.setSound(null, null)
                val manager = context
                        .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                try {

                    manager.createNotificationChannel(channel)
                } catch (npe: NullPointerException) {
                    Log.i(TAG, "Caught an NPE trying to create notification channel")
                }

                //Switched to NotificationCompat.Builder for eventual Android Wear functionality.
                val builder = NotificationCompat.Builder(
                        context,
                        Constants.PRIMARY_CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_menu_report_image)
                        .setContentTitle(context.getString(R.string.notification_title))
                        .setContentText(context.getString(R.string.notification_text))
                        .setContentIntent(resultPendingIntent)
                        .setOngoing(true)
                        .setWhen(System.currentTimeMillis())
                        .setOnlyAlertOnce(true)
                return builder.build()
            } else {
                val builder = NotificationCompat.Builder(context)

                        .setSmallIcon(android.R.drawable.ic_menu_report_image)
                        .setContentTitle(context.getString(R.string.notification_title))
                        .setContentText(context.getString(R.string.notification_text))
                        .setContentIntent(resultPendingIntent)
                        .setOngoing(true)
                        .setWhen(System.currentTimeMillis())
                        .setOnlyAlertOnce(true)
                return builder.build()
            }
        }
    }
}
