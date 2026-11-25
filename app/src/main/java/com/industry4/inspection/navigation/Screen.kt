package com.industry4.inspection.navigation

sealed class Screen(val route: String, val title: String) {
    object Dashboard : Screen("dashboard", "Dashboard")
    object LiveInspection : Screen("live_inspection", "Live Inspection")
    object ImageUpload : Screen("image_upload", "Image Testing")
    object History : Screen("history", "History")
    object InspectionDetail : Screen("inspection_detail/{id}", "Inspection Detail") {
        fun createRoute(id: String) = "inspection_detail/$id"
    }
    object Performance : Screen("performance", "Edge Performance")
    object Settings : Screen("settings", "Settings")
}
