package com.example.helmetcompanion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.helmetcompanion.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }

    private val crashFragment = CrashAlertFragment()
    private val navigationFragment = NavigationFragment()
    private val contactsFragment = EmergencyContactsFragment()
    private val settingsFragment = SettingsFragment()

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.connectHelmet()
        }

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            runCatching {
                val account = task.result
                val idToken = account.idToken
                if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank() || idToken.isNullOrBlank()) {
                    Toast.makeText(
                        this,
                        "Google sign-in finished, but Firebase sync needs GOOGLE_WEB_CLIENT_ID.",
                        Toast.LENGTH_LONG
                    ).show()
                    // ❌ REMOVED: maybeShowProfileSetup(force = true)
                } else {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnSuccessListener {
                            viewModel.refreshCloudData()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
                            // ❌ REMOVED: maybeShowProfileSetup(force = true)
                        }
                }
            }.onFailure {
                Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show()
                // ❌ REMOVED: maybeShowProfileSetup(force = true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FIX: Dynamically build the options to prevent crashing on an empty string
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()

        // Only request the ID token if the build config string actually contains something
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            gsoBuilder.requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        } else {
            Toast.makeText(this, "Warning: Web Client ID is missing. Google Sign-In to Firebase will fail.", Toast.LENGTH_LONG).show()
        }

        googleSignInClient = GoogleSignIn.getClient(this, gsoBuilder.build())

        if (savedInstanceState == null) {
            loadFragment(crashFragment)
        }

        binding.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_crash -> loadFragment(crashFragment)
                R.id.nav_navigation -> loadFragment(navigationFragment)
                R.id.nav_contacts -> loadFragment(contactsFragment)
                R.id.nav_settings -> loadFragment(settingsFragment)
            }
            true
        }

        observeSharedState()
        requestCorePermissions()
        ensureSignedIn()
    }

    override fun onStart() {
        super.onStart()
        viewModel.connectHelmet()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            viewModel.disconnectHelmet()
        }
    }

    fun startGoogleSignIn() {
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun observeSharedState() {
        viewModel.helmetEvent.observe(this) { event ->
            viewModel.handleHelmetEvent(event)
            if (event != null) {
                viewModel.consumeHelmetEvent()
            }
        }

        viewModel.crashAlertState.observe(this) { state ->
            val shouldShowDialog = state.phase == CrashAlertPhase.CRASH_DETECTED ||
                state.phase == CrashAlertPhase.COUNTDOWN ||
                state.phase == CrashAlertPhase.SOS_SENDING
            val existing = supportFragmentManager.findFragmentByTag(CrashCountdownDialogFragment.TAG)
            if (shouldShowDialog && existing == null) {
                CrashCountdownDialogFragment().show(
                    supportFragmentManager,
                    CrashCountdownDialogFragment.TAG
                )
            } else if (!shouldShowDialog && existing is CrashCountdownDialogFragment) {
                existing.dismissAllowingStateLoss()
            }
        }

        viewModel.onboardingRequired.observe(this) { required ->
            // FIX: Only show the profile dialog if the user is ACTUALLY signed into Firebase
            if (required == true && FirebaseAuth.getInstance().currentUser != null) {
                maybeShowProfileSetup(force = true)
            }
        }
    }

    private fun ensureSignedIn() {
        if (FirebaseAuth.getInstance().currentUser == null) {
            startGoogleSignIn()
        } else {
            viewModel.refreshCloudData()
        }
    }

    private fun maybeShowProfileSetup(force: Boolean) {
        if (!force) return
        val existing = supportFragmentManager.findFragmentByTag(ProfileSetupDialogFragment.TAG)
        if (existing == null) {
            ProfileSetupDialogFragment().show(
                supportFragmentManager,
                ProfileSetupDialogFragment.TAG
            )
        }
    }

    private fun requestCorePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
