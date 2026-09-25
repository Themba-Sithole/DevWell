package za.co.devwell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import za.co.devwell.notifications.createNotificationChannels
import za.co.devwell.ui.navigation.DevWellApp
import za.co.devwell.ui.theme.DevWellTheme
import za.co.devwell.viewmodel.DevWellViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannels(this)
        enableEdgeToEdge()
        setContent {
            DevWellTheme {
                val viewModel: DevWellViewModel = viewModel()
                DevWellApp(viewModel)
            }
        }
    }
}