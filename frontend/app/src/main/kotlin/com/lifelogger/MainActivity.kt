package com.lifelogger

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lifelogger.util.PermissionUtil

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupNavigation()
        handleRecordPermission()
        startLifeLoggerService()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionUtil.REQUEST_CODE_RECORD_AUDIO) {
            // Service will check permission itself before creating AudioRecord
            startLifeLoggerService()
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)
    }

    private fun handleRecordPermission() {
        if (!PermissionUtil.hasRecordAudioPermission(this)) {
            PermissionUtil.requestRecordAudioPermission(this)
        }
    }

    private fun startLifeLoggerService() {
        if (PermissionUtil.hasRecordAudioPermission(this)) {
            val intent = Intent(this, LifeLoggerService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }
}
