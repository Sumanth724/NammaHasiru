package com.nammahasiru.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.nammahasiru.app.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private var currentPhotoUri: Uri? = null

    // ── Per-user profile prefs ────────────────────────────────────────────────
    // Key: "profile_<username>"  e.g. "profile_sana", "profile_john"
    // This guarantees each user sees only their own saved name/phone/city/photo.
    private val AUTH_PREFS = "namma_hasiru_auth"
    private fun profilePrefsName(): String {
        val username = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE)
            .getString("logged_in_user", "default") ?: "default"
        return "profile_$username"
    }

    // ── Gallery picker ───────────────────────────────────────────────────────
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { applyPhoto(it) } }

    // ── Camera ───────────────────────────────────────────────────────────────
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) currentPhotoUri?.let { applyPhoto(it) } }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
    }

    // ────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Profile"
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadProfile()   // ← populate fields from saved prefs

        binding.btnChangePhoto.setOnClickListener { showPhotoOptions() }
        binding.ivProfileAvatar.setOnClickListener { showPhotoOptions() }
        binding.btnSaveProfile.setOnClickListener { saveProfile() }
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    private fun loadProfile() {
        val profilePrefs = getSharedPreferences(profilePrefsName(), MODE_PRIVATE)
        val authPrefs    = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE)

        val username    = authPrefs.getString("logged_in_user", "") ?: ""
        val defaultName = authPrefs.getString("user_${username}_name", username) ?: username

        val name        = profilePrefs.getString("name",  defaultName) ?: defaultName
        val phone       = profilePrefs.getString("phone", "") ?: ""
        val city        = profilePrefs.getString("city",  "") ?: ""
        val photoUriStr = profilePrefs.getString("photo_uri", null)

        binding.etName.setText(name)
        binding.etPhone.setText(phone)
        binding.etCity.setText(city)

        if (!photoUriStr.isNullOrEmpty()) {
            showPhotoFromUri(Uri.parse(photoUriStr))
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    private fun saveProfile() {
        val name  = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val city  = binding.etCity.text.toString().trim()

        if (name.isEmpty()) {
            binding.tilName.error = "Name is required"
            return
        }
        binding.tilName.error = null

        // Save to per-user profile prefs
        getSharedPreferences(profilePrefsName(), MODE_PRIVATE)
            .edit()
            .putString("name",  name)
            .putString("phone", phone)
            .putString("city",  city)
            .also { editor ->
                currentPhotoUri?.let { editor.putString("photo_uri", it.toString()) }
            }
            .apply()

        // Keep the auth display-name in sync (shown in drawer header)
        val authPrefs = getSharedPreferences(AUTH_PREFS, MODE_PRIVATE)
        val username  = authPrefs.getString("logged_in_user", "") ?: ""
        if (username.isNotEmpty()) {
            authPrefs.edit().putString("user_${username}_name", name).apply()
        }

        Toast.makeText(
            this,
            "✅ Profile saved! Name: $name, City: $city 🌿",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    // ── Photo picker dialog ───────────────────────────────────────────────────

    private fun showPhotoOptions() {
        AlertDialog.Builder(this)
            .setTitle("Change Profile Photo")
            .setItems(arrayOf("📷 Take Photo", "🖼️ Choose from Gallery", "Cancel")) { _, which ->
                when (which) {
                    0 -> requestCameraOrLaunch()
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun requestCameraOrLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "profile_photo")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        currentPhotoUri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )
        currentPhotoUri?.let { cameraLauncher.launch(it) }
    }

    // ── Apply and persist photo ───────────────────────────────────────────────

    private fun applyPhoto(uri: Uri) {
        // Try to keep read permission across restarts (gallery URIs need this)
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) { /* camera/MediaStore URIs don't need this */ }

        currentPhotoUri = uri
        showPhotoFromUri(uri)

        // Persist immediately to per-user prefs
        getSharedPreferences(profilePrefsName(), MODE_PRIVATE)
            .edit().putString("photo_uri", uri.toString()).apply()
    }

    private fun showPhotoFromUri(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .placeholder(R.drawable.ic_app_logo)
            .error(R.drawable.ic_app_logo)
            .into(binding.ivProfileAvatar)
        currentPhotoUri = uri
    }
}
