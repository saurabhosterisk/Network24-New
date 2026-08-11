package com.network24.player.features.live.activity

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.internal.NavigationMenuView
import com.network24.player.R
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.core.sync.SyncManager
import com.network24.player.features.dashboard.activity.DashboardActivity
import com.network24.player.features.login.activity.LoginActivity
import com.network24.player.features.settings.activity.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Binds the existing Live right-side drawer menu to the Live With EPG screen. */
class EpgDrawerMenuBinder @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var bound = false
    private var backCallback: OnBackPressedCallback? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (bound) return
        val activity = context as? EpgChannelListActivity ?: return
        val drawer = activity.findViewById<DrawerLayout>(R.id.drawerLayout) ?: return
        val more = activity.findViewById<View>(R.id.btnMore) ?: return
        val nav = activity.findViewById<com.google.android.material.navigation.NavigationView>(R.id.rightNav) ?: return

        bound = true
        more.setOnClickListener { drawer.openDrawer(GravityCompat.END) }

        nav.setNavigationItemSelectedListener { item ->
            drawer.closeDrawer(GravityCompat.END)
            when (item.itemId) {
                R.id.action_home -> {
                    activity.startActivity(Intent(activity, DashboardActivity::class.java))
                    activity.finish()
                    true
                }
                R.id.action_manage_categories -> {
                    activity.startActivity(Intent(activity, ManageCategoriesActivity::class.java))
                    true
                }
                R.id.action_refresh_all -> {
                    invokePrivate(activity, "loadChannels")
                    true
                }
                R.id.action_refresh_guide -> {
                    refreshGuide(activity)
                    true
                }
                R.id.action_settings -> {
                    activity.startActivity(Intent(activity, SettingsActivity::class.java))
                    true
                }
                R.id.action_logout -> {
                    PreferenceManager(activity).clear()
                    activity.startActivity(Intent(activity, LoginActivity::class.java))
                    activity.finishAffinity()
                    true
                }
                else -> false
            }
        }

        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                if (drawerView.id != R.id.rightNav) return
                nav.post {
                    val menuView = nav.getChildAt(0) as? NavigationMenuView
                    for (i in 0 until (menuView?.childCount ?: 0)) {
                        val child = menuView?.getChildAt(i) ?: continue
                        if (child.isFocusable) {
                            child.requestFocus()
                            break
                        }
                    }
                }
            }
        })

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.END)) {
                    drawer.closeDrawer(GravityCompat.END)
                } else {
                    isEnabled = false
                    activity.onBackPressedDispatcher.onBackPressed()
                }
            }
        }.also { activity.onBackPressedDispatcher.addCallback(activity, it) }
    }

    override fun onDetachedFromWindow() {
        backCallback?.remove()
        backCallback = null
        bound = false
        super.onDetachedFromWindow()
    }

    private fun invokePrivate(activity: EpgChannelListActivity, methodName: String) {
        try {
            activity.javaClass.getDeclaredMethod(methodName).apply { isAccessible = true }.invoke(activity)
        } catch (e: Exception) {
            Toast.makeText(activity, e.message ?: "Unable to refresh channels", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshGuide(activity: EpgChannelListActivity) {
        showLoader(activity, "Updating TV Guide…")
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = SyncManager(activity).syncFullEpg(force = true)
                withContext(Dispatchers.Main) {
                    hideLoader(activity)
                    if (result is com.network24.player.core.sync.SyncResult.Error) {
                        Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(activity, "TV Guide Updated", Toast.LENGTH_SHORT).show()
                        invokePrivate(activity, "loadGuideData")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoader(activity)
                    Toast.makeText(activity, e.message ?: "TV Guide update failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLoader(activity: EpgChannelListActivity, message: String) {
        try {
            activity.javaClass.superclass?.getDeclaredMethod("showLoader", String::class.java)?.apply { isAccessible = true }?.invoke(activity, message)
        } catch (_: Exception) { }
    }

    private fun hideLoader(activity: EpgChannelListActivity) {
        try {
            activity.javaClass.superclass?.getDeclaredMethod("hideLoader")?.apply { isAccessible = true }?.invoke(activity)
        } catch (_: Exception) { }
    }
}
