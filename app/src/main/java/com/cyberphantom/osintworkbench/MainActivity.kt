package com.cyberphantom.osintworkbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cyberphantom.osintworkbench.ui.WorkbenchApp
import com.cyberphantom.osintworkbench.ui.telegram.PrivateSessionHost
import com.cyberphantom.osintworkbench.ui.design.WorkbenchTheme

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        PrivateSessionHost.resume()
    }

    override fun onPause() {
        PrivateSessionHost.pause()
        super.onPause()
    }

    override fun onDestroy() {
        PrivateSessionHost.destroy()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkbenchTheme {
                WorkbenchApp()
            }
        }
    }
}
