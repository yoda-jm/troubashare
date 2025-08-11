package com.troubashare.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    data object GroupSelection : Screen("group_selection")
    
    data object Home : Screen("home/{groupId}") {
        const val GROUP_ID_ARG = "groupId"
        
        val arguments = listOf(
            navArgument(GROUP_ID_ARG) {
                type = NavType.StringType
            }
        )
        
        fun createRoute(groupId: String): String {
            return route.replace("{$GROUP_ID_ARG}", groupId)
        }
    }
    
    data object Library : Screen("library/{groupId}") {
        const val GROUP_ID_ARG = "groupId"
        
        val arguments = listOf(
            navArgument(GROUP_ID_ARG) {
                type = NavType.StringType
            }
        )
        
        fun createRoute(groupId: String): String {
            return route.replace("{$GROUP_ID_ARG}", groupId)
        }
    }
    data object Setlists : Screen("setlists/{groupId}") {
        const val GROUP_ID_ARG = "groupId"
        
        val arguments = listOf(
            navArgument(GROUP_ID_ARG) {
                type = NavType.StringType
            }
        )
        
        fun createRoute(groupId: String): String {
            return route.replace("{$GROUP_ID_ARG}", groupId)
        }
    }
    data object Settings : Screen("settings")
    
    data object SongDetails : Screen("song_details/{groupId}/{songId}") {
        const val GROUP_ID_ARG = "groupId"
        const val SONG_ID_ARG = "songId"
        
        val arguments = listOf(
            navArgument(GROUP_ID_ARG) {
                type = NavType.StringType
            },
            navArgument(SONG_ID_ARG) {
                type = NavType.StringType
            }
        )
        
        fun createRoute(groupId: String, songId: String): String {
            return route.replace("{$GROUP_ID_ARG}", groupId)
                       .replace("{$SONG_ID_ARG}", songId)
        }
    }
    
    data object SetlistEditor : Screen("setlist_editor/{setlistId}") {
        const val SETLIST_ID_ARG = "setlistId"
        
        val arguments = listOf(
            navArgument(SETLIST_ID_ARG) {
                type = NavType.StringType
            }
        )
        
        fun createRoute(setlistId: String): String {
            return route.replace("{$SETLIST_ID_ARG}", setlistId)
        }
    }
    
    data object ConcertMode : Screen("concert_mode/{setlistId}/{memberId}") {
        const val SETLIST_ID_ARG = "setlistId"
        const val MEMBER_ID_ARG = "memberId"
        
        val arguments = listOf(
            navArgument(SETLIST_ID_ARG) {
                type = NavType.StringType
            },
            navArgument(MEMBER_ID_ARG) {
                type = NavType.StringType
            }
        )
        
        fun createRoute(setlistId: String, memberId: String): String {
            return route.replace("{$SETLIST_ID_ARG}", setlistId)
                       .replace("{$MEMBER_ID_ARG}", memberId)
        }
    }
    
    data object FileViewer : Screen("file_viewer/{filePath}/{fileName}/{fileType}/{songTitle}/{memberName}") {
        const val FILE_PATH_ARG = "filePath"
        const val FILE_NAME_ARG = "fileName"
        const val FILE_TYPE_ARG = "fileType"
        const val SONG_TITLE_ARG = "songTitle"
        const val MEMBER_NAME_ARG = "memberName"
        
        val arguments = listOf(
            navArgument(FILE_PATH_ARG) {
                type = NavType.StringType
            },
            navArgument(FILE_NAME_ARG) {
                type = NavType.StringType
            },
            navArgument(FILE_TYPE_ARG) {
                type = NavType.StringType
            },
            navArgument(SONG_TITLE_ARG) {
                type = NavType.StringType
            },
            navArgument(MEMBER_NAME_ARG) {
                type = NavType.StringType
            }
        )
        
        fun createRoute(filePath: String, fileName: String, fileType: String, songTitle: String, memberName: String): String {
            return route.replace("{$FILE_PATH_ARG}", java.net.URLEncoder.encode(filePath, "UTF-8"))
                       .replace("{$FILE_NAME_ARG}", java.net.URLEncoder.encode(fileName, "UTF-8"))
                       .replace("{$FILE_TYPE_ARG}", fileType)
                       .replace("{$SONG_TITLE_ARG}", java.net.URLEncoder.encode(songTitle, "UTF-8"))
                       .replace("{$MEMBER_NAME_ARG}", java.net.URLEncoder.encode(memberName, "UTF-8"))
        }
    }
}