package com.industry4.inspection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.industry4.inspection.navigation.AppNavigation
import com.industry4.inspection.ui.theme.Industry4InspectionTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as Industry4Application

        setContent {
            Industry4InspectionTheme {
                val navController = rememberNavController()
                AppNavigation(
                    navController = navController,
                    app = app
                )
            }
        }
    }
}
