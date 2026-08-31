package com.hanifedma.tally

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.hanifedma.tally.ui.TallyApp
import com.hanifedma.tally.ui.TallyViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: TallyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the system splash until the app knows whether it is signed in,
        // so it never flashes a sign-in screen at someone who already is.
        val splash = installSplashScreen()
        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        lifecycleScope.launch {
            vm.ui.first { !it.booting }
            ready = true
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TallyApp(vm) }
    }

    override fun onResume() {
        super.onResume()
        // A socket can die without saying so — a lid closed, a phone asleep.
        // Coming back to the app is the moment to find out.
        vm.refresh()
    }
}
