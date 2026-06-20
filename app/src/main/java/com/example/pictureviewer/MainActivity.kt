package com.example.pictureviewer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pictureviewer.ui.library.LibraryScreen
import com.example.pictureviewer.ui.reader.ReaderScreen
import com.example.pictureviewer.ui.rename.RenameScreen
import com.example.pictureviewer.ui.theme.PictureViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PictureViewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PictureViewerNavHost()
                }
            }
        }
    }
}

@Composable
fun PictureViewerNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "library"
    ) {
        composable("library") {
            LibraryScreen(
                onComicClick = { comicUri, _ ->
                    val encoded = java.net.URLEncoder.encode(comicUri, "UTF-8")
                    navController.navigate("reader/$encoded")
                },
                onRenameClick = {
                    navController.navigate("rename")
                },
                onRenameFolderClick = { folderUri, rootUri ->
                    // 使用 Base64 编码避免特殊字符问题
                    android.util.Log.d("Navigation", "onRenameFolderClick: folderUri=$folderUri, rootUri=$rootUri")
                    val encodedFolder = android.util.Base64.encodeToString(
                        folderUri.toByteArray(Charsets.UTF_8),
                        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                    )
                    val encodedRoot = rootUri?.let {
                        android.util.Base64.encodeToString(
                            it.toByteArray(Charsets.UTF_8),
                            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                        )
                    } ?: ""
                    navController.navigate("rename/$encodedFolder/$encodedRoot")
                }
            )
        }
        composable(
            route = "reader/{comicUri}",
            arguments = listOf(
                navArgument("comicUri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val comicUri = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("comicUri") ?: return@composable, "UTF-8"
            )
            ReaderScreen(
                comicUri = comicUri,
                onBack = { navController.popBackStack() }
            )
        }
        composable("rename") {
            RenameScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "rename/{folderUri}/{rootUri}",
            arguments = listOf(
                navArgument("folderUri") { type = NavType.StringType },
                navArgument("rootUri") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val folderUriBase64 = backStackEntry.arguments?.getString("folderUri") ?: return@composable
            val rootUriBase64 = backStackEntry.arguments?.getString("rootUri") ?: ""
            // Base64 解码
            val folderUri = String(
                android.util.Base64.decode(folderUriBase64, android.util.Base64.URL_SAFE),
                Charsets.UTF_8
            )
            val rootUri = if (rootUriBase64.isNotEmpty()) {
                String(
                    android.util.Base64.decode(rootUriBase64, android.util.Base64.URL_SAFE),
                    Charsets.UTF_8
                )
            } else {
                null
            }
            android.util.Log.d("Navigation", "rename: folderUri=$folderUri, rootUri=$rootUri")
            RenameScreen(
                initialFolderUri = folderUri,
                initialRootUri = rootUri,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
