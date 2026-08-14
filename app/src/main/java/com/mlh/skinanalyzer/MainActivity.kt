package com.mlh.skinanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mlh.skinanalyzer.ui.AppNav
import com.mlh.skinanalyzer.ui.theme.MlhTheme
import com.mlh.skinanalyzer.ui.theme.Paper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MlhTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
                    AppNav()
                }
            }
        }
    }
}
