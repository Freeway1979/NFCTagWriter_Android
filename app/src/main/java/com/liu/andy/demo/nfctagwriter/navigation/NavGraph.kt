package com.liu.andy.demo.nfctagwriter.navigation

sealed class Screen(val route: String) {
    object NTag424 : Screen("ntag424")
    object NTag21X : Screen("ntag21x")
    object Settings : Screen("settings")
    object DeepLink : Screen("deeplink")
}

