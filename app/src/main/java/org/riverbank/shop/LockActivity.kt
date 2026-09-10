package org.riverbank.shop

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import org.riverbank.shop.data.Prefs
import org.riverbank.shop.databinding.ActivityLockBinding

/**
 * The app lock. Authentication is handled entirely by the platform keyguard —
 * Riverbank never sees a biometric template, and nothing about the unlock
 * leaves the device.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockBinding
    private lateinit var prefs: Prefs

    private val allowedAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        binding = ActivityLockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.unlockButton.setOnClickListener { prompt() }

        val status = BiometricManager.from(this).canAuthenticate(allowedAuthenticators)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            // Nothing is enrolled: fail open rather than locking the user out of
            // their own shopping account, and turn the setting back off.
            prefs.disableAppLock()
            Toast.makeText(this, R.string.lock_unavailable, Toast.LENGTH_LONG).show()
            unlockAndClose()
            return
        }
        prompt()
    }

    private fun prompt() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlockAndClose()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        finishAffinity()
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_prompt_title))
            .setSubtitle(getString(R.string.lock_prompt_subtitle))
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()
        prompt.authenticate(info)
    }

    private fun unlockAndClose() {
        AppLock.markUnlocked()
        finish()
    }
}
