package com.memorypin.ui

import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.memorypin.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 앱 시작 시 브랜드 진입 모션을 보여주고 메인 화면으로 넘긴다.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepareViews()
        playEnterMotion()
        navigateToMain()
    }

    private fun prepareViews() {
        binding.splashIconHolder.apply {
            alpha = 0f
            scaleX = 0.55f
            scaleY = 0.55f
        }
        binding.splashTitle.apply {
            alpha = 0f
            translationY = 28f
        }
        binding.splashSubtitle.alpha = 0f
    }

    private fun playEnterMotion() {
        binding.splashIconHolder.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(520)
            .setInterpolator(OvershootInterpolator(1.15f))
            .start()

        binding.splashTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(220)
            .setDuration(420)
            .setInterpolator(DecelerateInterpolator())
            .start()

        binding.splashSubtitle.animate()
            .alpha(0.9f)
            .setStartDelay(380)
            .setDuration(360)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun navigateToMain() {
        lifecycleScope.launch {
            delay(TOTAL_DURATION_MS)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    companion object {
        private const val TOTAL_DURATION_MS = 1_350L
    }
}
