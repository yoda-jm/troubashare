package com.troubashare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.troubashare.ui.screens.group.SimpleGroupSelectionScreen
import com.troubashare.ui.screens.home.HomeScreen
import com.troubashare.ui.screens.library.LibraryScreen
import com.troubashare.ui.screens.song.SongDetailScreen
import com.troubashare.ui.screens.setlist.SetlistsScreen
import com.troubashare.ui.screens.settings.SettingsScreen
import com.troubashare.ui.screens.file.FileViewerScreen
import com.troubashare.domain.model.SongFile
import java.net.URLDecoder

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
                    navController.navigate(Screen.Setlists.createRoute(groupId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onSwitchGroup = { newGroupId ->
                    navController.navigate(Screen.Home.createRoute(newGroupId)) {
                        popUpTo(Screen.Home.route) {
                            inclusive = true
                        }
                    }
                },
                onCreateNewGroup = {
                    navController.navigate(Screen.GroupSelection.route) {
                        popUpTo(Screen.Home.route) {
                            inclusive = true
                        }
                    }
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
                },
                onSongClick = { songId ->
                    navController.navigate(Screen.SongDetails.createRoute(groupId, songId))
                }
            )
        }
        
        composable(
            route = Screen.SongDetails.route,
            arguments = Screen.SongDetails.arguments
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString(Screen.SongDetails.GROUP_ID_ARG) ?: ""
            val songId = backStackEntry.arguments?.getString(Screen.SongDetails.SONG_ID_ARG) ?: ""
            SongDetailScreen(
                groupId = groupId,
                songId = songId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onViewFile = { songFile, songTitle, memberName ->
                    navController.navigate(
                        Screen.FileViewer.createRoute(
                            filePath = songFile.filePath,
                            fileName = songFile.fileName,
                            fileType = songFile.fileType.name,
                            songTitle = songTitle,
                            memberName = memberName
                        )
                    )
                }
            )
        }
        
        composable(
            route = Screen.Setlists.route,
            arguments = Screen.Setlists.arguments
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString(Screen.Setlists.GROUP_ID_ARG) ?: ""
            SetlistsScreen(
                groupId = groupId,
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
        
        composable(
            route = Screen.FileViewer.route,
            arguments = Screen.FileViewer.arguments
        ) { backStackEntry ->
            val filePath = URLDecoder.decode(
                backStackEntry.arguments?.getString(Screen.FileViewer.FILE_PATH_ARG) ?: "",
                "UTF-8"
            )
            val fileName = URLDecoder.decode(
                backStackEntry.arguments?.getString(Screen.FileViewer.FILE_NAME_ARG) ?: "",
                "UTF-8"
            )
            val fileType = backStackEntry.arguments?.getString(Screen.FileViewer.FILE_TYPE_ARG) ?: ""
            val songTitle = URLDecoder.decode(
                backStackEntry.arguments?.getString(Screen.FileViewer.SONG_TITLE_ARG) ?: "",
                "UTF-8"
            )
            val memberName = URLDecoder.decode(
                backStackEntry.arguments?.getString(Screen.FileViewer.MEMBER_NAME_ARG) ?: "",
                "UTF-8"
            )
            
            FileViewerScreen(
                songFile = SongFile(
                    id = "",
                    songId = "",
                    memberId = "",
                    fileName = fileName,
                    filePath = filePath,
                    fileType = when(fileType.uppercase()) {
                        "PDF" -> com.troubashare.domain.model.FileType.PDF
                        else -> com.troubashare.domain.model.FileType.IMAGE
                    }
                ),
                songTitle = songTitle,
                memberName = memberName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}