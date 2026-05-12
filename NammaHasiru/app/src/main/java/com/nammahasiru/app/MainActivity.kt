package com.nammahasiru.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import com.nammahasiru.app.data.PlantDatabase
import com.nammahasiru.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var navController: NavController

    // Per-screen toolbar config: Triple(title, subtitle, showBell)
    data class ToolbarConfig(val title: String, val subtitle: String?, val showBell: Boolean)

    private val screenConfig = mapOf(
        R.id.homeFragment          to ToolbarConfig("Namma Hasiru", null, true),
        R.id.mapFragment           to ToolbarConfig("Plant Map",    null, false),
        R.id.statsFragment         to ToolbarConfig("Statistics",   null, false),
        R.id.addPlantFragment      to ToolbarConfig("Add Plant",    null, false),
        R.id.notificationsFragment to ToolbarConfig("Notifications",null, false),
        R.id.profileFragment       to ToolbarConfig("Profile",      null, false),
        R.id.settingsFragment      to ToolbarConfig("Settings",     null, false),
        R.id.plantDetailFragment   to ToolbarConfig("Plant Detail", null, false)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ── Toolbar ──────────────────────────────────────────────────────────
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Namma Hasiru"
        supportActionBar?.subtitle = null

        // ── Drawer toggle — hamburger at LEFT ────────────────────────────────
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener(this)

        // ── Populate drawer header with logged-in user ───────────────────────
        loadDrawerHeader()

        // ── NavController + BottomNav ────────────────────────────────────────
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        // Update toolbar per fragment
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val cfg = screenConfig[destination.id]
                ?: ToolbarConfig("Namma Hasiru", null, true)

            supportActionBar?.title    = cfg.title
            supportActionBar?.subtitle = cfg.subtitle

            // Hamburger on main tabs, back arrow on child screens
            val isMainTab = (destination.id == R.id.homeFragment || destination.id == R.id.mapFragment || destination.id == R.id.statsFragment || destination.id == R.id.plantListFragment || destination.id == R.id.addPlantFragment)
            drawerToggle.isDrawerIndicatorEnabled = isMainTab
            drawerToggle.syncState()

            if (destination.id == R.id.notificationsFragment || 
                destination.id == R.id.profileFragment || 
                destination.id == R.id.settingsFragment || 
                destination.id == R.id.plantDetailFragment ||
                destination.id == R.id.plantListFragment ||
                destination.id == R.id.addPlantFragment) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.show()
                binding.btnNotifications.visibility = if (cfg.showBell) android.view.View.VISIBLE else android.view.View.GONE
            }
            invalidateOptionsMenu()   // triggers onPrepareOptionsMenu
        }

        binding.btnNotifications.setOnClickListener {
            navController.navigate(R.id.notificationsFragment)
        }

        // Navigation is handled automatically by setupWithNavController
    }

    // ── Show/hide bell per screen ────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    // ── Populate drawer header ───────────────────────────────────────────────

    private fun loadDrawerHeader() {
        val authPrefs    = getSharedPreferences("namma_hasiru_auth", MODE_PRIVATE)

        val username = authPrefs.getString("logged_in_user", null) ?: return
        // Always use the latest saved display name (updated when profile is saved)
        val name     = authPrefs.getString("user_${username}_name", username) ?: username

        val headerView = binding.navigationView.getHeaderView(0)
        val tvName     = headerView.findViewById<TextView>(R.id.tvNavUserName)
        val tvHandle   = headerView.findViewById<TextView>(R.id.tvNavUserEmail)
        val ivAvatar   = headerView.findViewById<ImageView>(R.id.ivAvatar)

        tvName.text   = name
        tvHandle.text = "@$username"

        // ── Load profile photo from per-user prefs ────────────────────────────
        val userProfilePrefs = getSharedPreferences("profile_$username", MODE_PRIVATE)
        val photoUriStr  = userProfilePrefs.getString("photo_uri", null)
        if (!photoUriStr.isNullOrEmpty()) {
            Glide.with(this)
                .load(Uri.parse(photoUriStr))
                .circleCrop()
                .placeholder(R.drawable.ic_app_logo)
                .error(R.drawable.ic_app_logo)
                .into(ivAvatar)
        } else {
            ivAvatar.setImageResource(R.drawable.ic_app_logo)
        }

        // Tap avatar or name → open Profile
        val openProfile = {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            if (navController.currentDestination?.id != R.id.profileFragment) {
                navController.navigate(R.id.profileFragment)
            }
        }
        ivAvatar.setOnClickListener { openProfile() }
        tvName.setOnClickListener   { openProfile() }
    }

    // Refresh drawer every time we return from somewhere (could be profile edit)
    override fun onResume() {
        super.onResume()
        loadDrawerHeader()
    }

    // ── Drawer item handler ───────────────────────────────────────────────────────────────

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        when (item.itemId) {
            R.id.drawer_profile -> {
                if (navController.currentDestination?.id != R.id.profileFragment) {
                    navController.navigate(R.id.profileFragment)
                }
            }
            R.id.drawer_settings -> {
                if (navController.currentDestination?.id != R.id.settingsFragment) {
                    navController.navigate(R.id.settingsFragment)
                }
            }
            R.id.drawer_about  -> showAboutDialog()
            R.id.drawer_logout -> {
                val authPrefs = getSharedPreferences("namma_hasiru_auth", MODE_PRIVATE)
                authPrefs.edit().remove("logged_in_user").apply()
                // Release the DB so the next user gets a clean instance
                PlantDatabase.clearInstance()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
        }
        return true
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("🌿 About Namma Hasiru")
            .setMessage(
                "Namma Hasiru — ನಮ್ಮ ಹಸಿರು\n" +
                "Version 1.0.0\n\n" +
                "\"Our Green\" is a community-driven tree-planting tracker " +
                "built to help villages and neighbourhoods grow greener cities, " +
                "one sapling at a time.\n\n" +
                "✅ Track every plant you grow\n" +
                "📍 Geo-tag your saplings on a live map\n" +
                "🤖 AI-powered health analysis\n" +
                "🔔 90-day care reminders\n" +
                "📊 Survival rate statistics\n\n" +
                "Made with 💚 for a greener tomorrow.\n" +
                "© 2026 Namma Hasiru Team"
            )
            .setPositiveButton("Got it! 🌱") { d, _ -> d.dismiss() }
            .setIcon(android.R.drawable.ic_menu_info_details)
            .show()
    }

    // ── Back press ───────────────────────────────────────────────────────────

    override fun onBackPressed() {
        when {
            binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            navController.currentDestination?.id != R.id.homeFragment ->
                navController.navigateUp()
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }
}
