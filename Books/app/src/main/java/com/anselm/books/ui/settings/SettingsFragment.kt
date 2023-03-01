package com.anselm.books.ui.settings

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.lifecycle.Lifecycle
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.anselm.books.BooksApplication
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.BooksPreferences
import com.anselm.books.R
import com.anselm.books.TAG
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {
    private var preferenceListener: OnSharedPreferenceChangeListener? =
        OnSharedPreferenceChangeListener { _, key ->
            if (key == BooksPreferences.OCLC_KEY) {
                updateUseWorldcat()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        app.prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        handleMenu(requireActivity())

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        app.prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferenceListener = null
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Sets up for import.
        val importer = setupImport()
        findPreference<Preference>("import_preference")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                importer.launch("*/*")
                true
            }

        // Sets up for export.
        val exporter = setupExport()
        findPreference<Preference>("export_preference")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                exporter.launch("books.zip")
                true
            }

        findPreference<Preference>("reset_preference")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val builder = android.app.AlertDialog.Builder(requireActivity())
                builder.setMessage(getString(R.string.reset_database_confirmation))
                    .setPositiveButton(R.string.yes) { _, _ ->
                        app.applicationScope.launch {
                            app.repository.deleteAll()
                        }
                    }
                    .setNegativeButton(R.string.no) { _, _ -> }
                    .show()
                true
            }

        setupLastLocation()
        setupLookupServices()
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.forEach {
                    it.isVisible = false
                }
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupImport(): ActivityResultLauncher<String> {
        val app = BooksApplication.app
        val importExport = app.importExport
        return registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                val context = app.applicationContext
                Log.d(TAG, "Opening file $uri")
                if (uri == null) {
                    Log.d(TAG, "No file selected, nothing to import")
                    app.toast(R.string.select_import_file_prompt)
                } else {
                    var counts: Pair<Int, Int> = Pair(-1, -1)
                    var msg: String? = null
                    val reporter = app.openReporter(
                        getString(R.string.starting_importing),
                        isIndeterminate = false
                    )
                    app.applicationScope.launch {
                        try {
                            counts = importExport.importZipFile(uri, reporter)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to import books.", e)
                            msg = e.message
                        }
                    }.invokeOnCompletion {
                        // We're running on the application lifecycle scope, so this view that we're
                        // launching from might be done by the time we get here, protect against that.
                        val text = if (msg != null) {
                            context.getString(R.string.import_failed, msg)
                        } else {
                            context.getString(R.string.import_status, counts.first, counts.second)
                        }
                        reporter.close()
                        app.toast(text)
                    }
                }
            }
    }

    private fun setupExport(): ActivityResultLauncher<String> {
        val app = BooksApplication.app
        val context = app.applicationContext
        val importExport = app.importExport
        return registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            if (uri == null) {
                Log.d(TAG, "Failed to select directory tree.")
                app.toast("Select a file to export to.")
            } else {
                Log.d(TAG, "Opening directory $uri")
                val reporter = app.openReporter(getString(R.string.exporting_books), isIndeterminate = false)
                var count = 0
                var msg: String? = null
                app.applicationScope.launch {
                    try {
                        count = importExport.exportZipFile(uri, reporter)
                    } catch (e: Exception) {
                        Log.e(TAG, "Export to $uri failed.", e)
                        msg = e.TAG
                    }
                }.invokeOnCompletion {
                    val text = if (msg != null) {
                        context.getString(R.string.export_failed, msg)
                    } else {
                        context.getString(R.string.export_status, count)
                    }
                    reporter.close()
                    app.toast(text)
                }
            }
        }
    }

    private fun setupLastLocation() {
        // Handles the "last location" preference using the hidden "lookup_use_last_location_value"
        // preference.
        val locationCheckBox = findPreference<CheckBoxPreference>(BooksPreferences.USE_LAST_LOCATION)!!
        val locationValue = findPreference<EditTextPreference>(BooksPreferences.USE_LAST_LOCATION_VALUE)!!
        findPreference<PreferenceCategory>("preferences_import_options")
            ?.removePreference(locationValue)
        val prefs = preferenceManager.sharedPreferences
        if (prefs?.getBoolean(BooksPreferences.USE_LAST_LOCATION, true) == true) {
            locationCheckBox.summary = locationValue.text
        } else {
            locationCheckBox.summary = ""
        }
        locationCheckBox.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, newValue ->
                if (!(newValue as Boolean)) {
                    locationCheckBox.summary = ""
                }
                true
            }
    }

    private fun displayLookupServiceStats() {
        app.lookupService.clientKeys {
            val preference = findPreference<CheckBoxPreference>(it)
            val (lookupCount, matchCount, coverCount) = app.lookupService.stats(it)
            preference?.summary = getString(R.string.preferences_lookup_service_stats,
                lookupCount, matchCount, coverCount
            )
        }
    }

    private fun setupLookupServices() {
        findPreference<Preference>("lookup_service_reset_stats")?.setOnPreferenceClickListener {
            Log.d(TAG, "Pref")
            AlertDialog.Builder(requireActivity())
                .setTitle("Really reset lookup statistics?")
                .setPositiveButton(R.string.yes) {  _, _ ->
                    app.lookupService.resetStats()
                    displayLookupServiceStats()
                }
                .setNegativeButton(R.string.no) { _, _ -> }
                .create().show()
            true
        }
        displayLookupServiceStats()
        updateUseWorldcat()
    }

    private fun updateUseWorldcat() {
        val summary = app.getString(R.string.worldcat_requires_wskey)
        val wskey = app.prefs.getString(BooksPreferences.OCLC_KEY, "")
        val useWorldcat = findPreference<CheckBoxPreference>(BooksPreferences.USE_WORLDCAT)!!
        if (wskey == null || wskey.isEmpty()) {
            useWorldcat.isEnabled = false
            useWorldcat.summary = summary
        } else {
            useWorldcat.isEnabled = true
            if (useWorldcat.summary == summary) {
                useWorldcat.summary = ""
            }
        }

    }
}

