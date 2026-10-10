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

        super.onCreate(savedInstanceState)
        // After super.onCreate, not before. The view model keeps what was
        // being looked at in the activity's saved state, and until that line
        // has run there is no saved state to hand it: asking for the view
        // model any earlier is not an early start, it is a crash.
        lifecycleScope.launch {
            vm.ui.first { !it.booting }
            ready = true
        }
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
