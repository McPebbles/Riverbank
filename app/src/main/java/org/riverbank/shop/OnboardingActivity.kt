package org.riverbank.shop

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.riverbank.shop.data.Prefs
import org.riverbank.shop.data.Storefront
import org.riverbank.shop.databinding.ActivityOnboardingBinding
import org.riverbank.shop.databinding.ItemStorefrontBinding

/**
 * First run: pick a storefront. This is the only screen that ever appears
 * before the storefront itself, and it collects nothing.
 */
class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = Prefs(this)
        for (storefront in Storefront.entries) {
            val button = ItemStorefrontBinding.inflate(
                layoutInflater,
                binding.storefrontList,
                false
            ).root
            button.text = storefront.label
            button.setOnClickListener {
                prefs.storefront = storefront
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            }
            binding.storefrontList.addView(button)
        }
    }
}
