package com.example.helmetcompanion

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.content.pm.PackageManager


class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var crashFragment: CrashAlertFragment

    private val SMS_PERMISSION_CODE = 101
    private val locationPermissionCode = 102



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestSmsPermission()
        requestLocationPermission()


        crashFragment = CrashAlertFragment()
        loadFragment(crashFragment)

        bluetoothManager = BluetoothManager(this) { message ->
            if (message.contains("CRASH"))
                {
                runOnUiThread {
                    crashFragment.triggerCrash()
                }
            }
        }

        // 🔵 CHANGE THIS to your HC-05 Bluetooth name
        bluetoothManager.connect("HC-05")

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_crash -> loadFragment(crashFragment)
//                R.id.nav_navigation -> loadFragment(
//                    PlaceholderFragment("Navigation coming soon")
//                )

                R.id.nav_contacts -> {
                    loadFragment(EmergencyContactsFragment())
                }

                R.id.nav_settings -> loadFragment(
                    PlaceholderFragment("Settings coming soon")
                )

                R.id.nav_navigation -> {
                    loadFragment(NavigationFragment())
                    true
                }


            }
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun requestSmsPermission() {
        if (checkSelfPermission(android.Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.SEND_SMS),
                SMS_PERMISSION_CODE
            )
        }
    }

    private fun requestLocationPermission() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionCode
            )
        }
    }


}


