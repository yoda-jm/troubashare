package com.troubashare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.troubashare.ui.screens.group.SimpleGroupSelectionScreen
import com.troubashare.ui.screens.home.HomeScreen
import com.troubashare.ui.screens.library.LibraryScreen
import com.troubashare.ui.screens.setlist.SetlistsScreen
import com.troubashare.ui.screens.settings.SettingsScreen

@Composable
fun TroubaShareNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.GroupSelection.route
    ) {
        composable(Screen.GroupSelection.route) {
            SimpleGroupSelectionScreen(
                onGroupSelected = { groupId ->
                    navController.navigate(Screen.Home.createRoute(groupId)) {
                        // Clear the back stack to prevent going back to group selection
                        popUpTo(Screen.GroupSelection.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }
        
        composable(
            route = Screen.Home.route,
            arguments = Screen.Home.arguments
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString(Screen.Home.GROUP_ID_ARG) ?: ""
            HomeScreen(
                groupId = groupId,
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library.createRoute(groupId))
                },
                onNavigateToSetlists = {
                    navController.navigate(Screen.Setlists.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        
        composable(
            route = Screen.Library.route,
            arguments = Screen.Library.arguments
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString(Screen.Library.GROUP_ID_ARG) ?: ""
            LibraryScreen(
                groupId = groupId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Setlists.route) {
            SetlistsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}