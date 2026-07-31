package ir.sabou.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ir.sabou.inventory.ui.SabouApp
import ir.sabou.inventory.ui.theme.SabouTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SabouTheme {
                SabouApp((application as SabouApplication).container)
            }
        }
    }
}

