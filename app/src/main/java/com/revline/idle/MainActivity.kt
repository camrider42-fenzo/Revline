package com.revline.idle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revline.idle.ui.RevlineApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF000000.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF000000.toInt()),
        )
        setContent {
            val vm: GameViewModel = viewModel()
            RevlineApp(vm)
        }
    }
}
