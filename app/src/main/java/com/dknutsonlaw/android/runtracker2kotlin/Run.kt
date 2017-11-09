package com.dknutsonlaw.android.runtracker2kotlin

/*
  Created by dck on 9/6/15.
   * Created by dck on 1/14/15.

  The basic object used to hold data concerning each particular run we've tracked or are tracking.

  2/15/2015
  Implemented Parcelable interface to allow a Run to be passed to TrackingLocationIntentService.

  5/1/2015
  Added mDistance and mDuration fields for live updating in RunRecyclerListFragment

  8/14/2015
  Added mStartAddress and mEndAddress fields
 */
import android.os.Parcel
import android.os.Parcelable

import java.util.Date
import java.util.Locale

class Run : Parcelable {

    internal var id: Long = -1
    internal var startDate: Date? = Date()
    internal var startAddress: String? = ""
    internal var distance: Double = 0.toDouble()
    internal var duration: Long = 0
    internal var endAddress: String? = ""

    /*init {
        id = -1
        startDate = Date()
        startAddress = ""
        distance = 0.0
        duration = 0
        endAddress = ""
    }*/

    override fun describeContents(): Int = 0


    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeLong(startDate!!.time)
        parcel.writeString(startAddress)
        parcel.writeDouble(distance)
        parcel.writeLong(duration)
        parcel.writeString(endAddress)
    }

    override fun toString(): String {
        return "RunID =" + id + "\n" +
                "Start Date = " + startDate!!.toString() + "\n" +
                "Start Address = " + startAddress + "\n" +
                "Distance = " + distance * Constants.METERS_TO_MILES + "\n" +
                "Duration = " + formatDuration(duration.toInt()) + "\n" +
                "End Address = " + endAddress
    }

    companion object {
        private val TAG = "com.dknutsonlaw.android.runtracker2kotlin.run"

        val CREATOR: Parcelable.Creator<Run> = object : Parcelable.Creator<Run> {
            override fun createFromParcel(source: Parcel): Run {
                val run = Run()
                run.id = source.readLong()
                run.startDate = Date(source.readLong())
                run.startAddress = source.readString()
                run.distance = source.readDouble()
                run.duration = source.readLong()
                run.endAddress = source.readString()
                return run
            }

            override fun newArray(size: Int): Array<Run> = Array(size, {_ -> Run()})

        }

        internal fun formatDuration(durationSeconds: Int): String {
            val seconds = durationSeconds % 60
            val minutes = (durationSeconds - seconds) / 60 % 60
            val hours = (durationSeconds - minutes * 60 - seconds) / 3600
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
}
