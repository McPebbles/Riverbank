package org.riverbank.shop

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.riverbank.shop.data.Prefs
import org.riverbank.shop.data.Storefront
import org.riverbank.shop.databinding.ActivitySettingsBinding
import org.riverbank.shop.privacy.BlockStats

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            val prefs = Prefs(requireContext())

            findPreference<ListPreference>(Prefs.KEY_STOREFRONT)?.apply {
                entries = Storefront.entries.map { it.label }.toTypedArray()
                entryValues = Storefront.entries.map { it.id }.toTypedArray()
                if (value == null) value = prefs.storefront.id
            }

            findPreference<Preference>(Prefs.KEY_BLOCKED_COUNT)?.apply {
                summary = getString(R.string.pref_blocked_count_summary, BlockStats.total())
                setOnPreferenceClickListener {
                    BlockStats.reset(prefs)
                    summary = getString(R.string.pref_blocked_count_summary, 0)
                    toast(R.string.blocked_reset)
                    true
                }
            }

            findPreference<ListPreference>(Prefs.KEY_DARK)?.setOnPreferenceChangeListener { _, value ->
                App.applyTheme(value as String)
                true
            }

            findPreference<SwitchPreferenceCompat>(Prefs.KEY_APP_LOCK)
                ?.setOnPreferenceChangeListener { _, value ->
                    if (value == true) AppLock.markUnlocked() else AppLock.lock()
                    true
                }

            findPreference<Preference>("version")?.summary =
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

            findPreference<Preference>("source")?.setOnPreferenceClickListener {
                open(getString(R.string.pref_source_summary)); true
            }
            findPreference<Preference>("privacy_policy")?.setOnPreferenceClickListener {
                open(getString(R.string.pref_source_summary) + "/blob/main/PRIVACY.md"); true
            }
        }

        private fun open(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (_: Exception) {
                toast(R.string.no_browser)
            }
        }

        private fun toast(resId: Int) =
            Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }
}
