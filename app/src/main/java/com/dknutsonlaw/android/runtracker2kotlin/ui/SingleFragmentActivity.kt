package com.dknutsonlaw.android.runtracker2kotlin.ui

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import com.dknutsonlaw.android.runtracker2kotlin.R

/**
 * Created by dck on 9/6/15.
 *
 *
 * Created by dck on 12/7/14.
 *
 * Taken from the Big Nerd Ranch book. This class is a general template for an Activity whose sole
 * purpose is to host a single Fragment that does all the real work of the Activity.
 */
abstract class SingleFragmentActivity : AppCompatActivity() {

    /*Override this function if a particular subclass needs a layout with more than just a
     *container for the Fragment we're hosting.
     */
    private val layoutResId: Int
        get() = R.layout.activity_fragment

    protected abstract fun createFragment(): Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutResId)

        val fm = supportFragmentManager
        var fragment: Fragment? = fm.findFragmentById(R.id.fragmentContainer)

        if (fragment == null) {
            fragment = createFragment()
            fm.beginTransaction().add(R.id.fragmentContainer, fragment).commit()
        }
    }

    companion object {
        private val TAG = "SingleFrameActivity"
    }
}
