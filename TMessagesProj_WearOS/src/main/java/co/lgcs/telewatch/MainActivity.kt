package co.lgcs.telewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import org.telegram.messenger.UserConfig
import co.lgcs.telewatch.presentation.AuthScreen
import co.lgcs.telewatch.presentation.ChatListScreen
import co.lgcs.telewatch.presentation.MessageScreen
import co.lgcs.telewatch.presentation.theme.WearAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WearAppTheme {
                val navController = rememberSwipeDismissableNavController()

                // Determine start destination based on auth state
                val startDest = if (UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated)
                    Screen.ChatList.route
                else
                    Screen.Auth.route

                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = startDest
                ) {
                    composable(Screen.Auth.route) {
                        AuthScreen(
                            onAuthenticated = {
                                navController.navigate(Screen.ChatList.route) {
                                    popUpTo(Screen.Auth.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.ChatList.route) {
                        ChatListScreen(
                            onChatSelected = { dialogId ->
                                navController.navigate(Screen.Messages.route(dialogId))
                            }
                        )
                    }

                    composable(
                        route = Screen.Messages.routeWithArgs,
                        arguments = listOf(navArgument("dialogId") { type = NavType.LongType })
                    ) { backStack ->
                        val dialogId = backStack.arguments?.getLong("dialogId") ?: return@composable
                        MessageScreen(
                            dialogId = dialogId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object ChatList : Screen("chat_list")
    object Messages : Screen("messages/{dialogId}") {
        val routeWithArgs = "messages/{dialogId}"
        fun route(dialogId: Long) = "messages/$dialogId"
    }
}
