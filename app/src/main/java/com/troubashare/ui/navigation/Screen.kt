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
    data object CloudSync : Screen("cloud_sync")
    data object GroupSharing : Screen("group_sharing/{groupId}") {
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
    data object JoinGroup : Screen("join_group")
    
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
    
    data object FileViewer : Screen(
        "file_viewer/{filePath}/{fileName}/{fileType}/{songTitle}/{memberName}?fileId={fileId}&memberId={memberId}&songId={songId}"
    ) {
        const val FILE_PATH_ARG  = "filePath"
        const val FILE_NAME_ARG  = "fileName"
        const val FILE_TYPE_ARG  = "fileType"
        const val SONG_TITLE_ARG = "songTitle"
        const val MEMBER_NAME_ARG = "memberName"
        const val FILE_ID_ARG    = "fileId"
        const val MEMBER_ID_ARG  = "memberId"
        const val SONG_ID_ARG    = "songId"

        val arguments = listOf(
            navArgument(FILE_PATH_ARG)  { type = NavType.StringType },
            navArgument(FILE_NAME_ARG)  { type = NavType.StringType },
            navArgument(FILE_TYPE_ARG)  { type = NavType.StringType },
            navArgument(SONG_TITLE_ARG) { type = NavType.StringType },
            navArgument(MEMBER_NAME_ARG){ type = NavType.StringType },
            navArgument(FILE_ID_ARG)    { type = NavType.StringType; defaultValue = "" },
            navArgument(MEMBER_ID_ARG)  { type = NavType.StringType; defaultValue = "" },
            navArgument(SONG_ID_ARG)    { type = NavType.StringType; defaultValue = "" }
        )

        fun createRoute(
            filePath: String,
            fileName: String,
            fileType: String,
            songTitle: String,
            memberName: String,
            fileId: String = "",
            memberId: String = "",
            songId: String = ""
        ): String {
            val enc = { s: String -> java.net.URLEncoder.encode(s, "UTF-8") }
            // memberName must be non-empty (empty path segments crash Navigation)
            val safeMemberName = enc(memberName.ifBlank { "_" })
            return "file_viewer/${enc(filePath)}/${enc(fileName)}/$fileType/${enc(songTitle)}/$safeMemberName" +
                   "?fileId=${enc(fileId)}&memberId=${enc(memberId)}&songId=${enc(songId)}"
        }
    }
}