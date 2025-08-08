package com.troubashare.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    object GroupSelection : Screen("group_selection")
    
    object Home : Screen("home/{groupId}") {
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
    
    object Library : Screen("library/{groupId}") {
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
    object Setlists : Screen("setlists")
    object Settings : Screen("settings")
    
    object SongDetails : Screen("song_details/{groupId}/{songId}") {
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
    
    object SetlistEditor : Screen("setlist_editor/{setlistId}") {
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
    
    object ConcertMode : Screen("concert_mode/{setlistId}/{memberId}") {
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
}