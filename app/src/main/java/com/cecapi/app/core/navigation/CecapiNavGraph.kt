package com.cecapi.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cecapi.app.feature.modulo3_asistenteinteligente.AiAssistantScreen
import com.cecapi.app.feature.modulo4_lectordocumentos.DocumentReaderScreen
import com.cecapi.app.feature.modulo7_entorno.EnvironmentScreen
import com.cecapi.app.feature.modulo6_aprendizaje.LearningScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.DashboardScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.HomeScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.LoginScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.RegisterScreen
import com.cecapi.app.feature.modulo1_aplicacionprincipal.SettingsScreen
import com.cecapi.app.feature.modulo5_solicitudes.RequestsScreen
import com.cecapi.app.feature.modulo2_asistentevoz.VoiceAssistantScreen

@Composable
fun CecapiNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = CecapiDestinations.HOME) {
        composable(CecapiDestinations.HOME) {
            HomeScreen(
                onNavigateToLogin = { navController.navigate(CecapiDestinations.LOGIN) },
                onNavigateToModule = { route -> navController.navigate(route) },
            )
        }
        composable(CecapiDestinations.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(CecapiDestinations.DASHBOARD) {
                        popUpTo(CecapiDestinations.HOME) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(CecapiDestinations.REGISTER) },
            )
        }
        composable(CecapiDestinations.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(CecapiDestinations.DASHBOARD) {
                        popUpTo(CecapiDestinations.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(CecapiDestinations.DASHBOARD) {
            DashboardScreen(
                onModuleClick = { route -> navController.navigate(route) },
                onSettingsClick = { navController.navigate(CecapiDestinations.SETTINGS) },
                onLoggedOut = {
                    navController.navigate(CecapiDestinations.HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(CecapiDestinations.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(CecapiDestinations.VOICE_ASSISTANT) {
            VoiceAssistantScreen(onNavigateToModule = { route -> navController.navigate(route) })
        }
        composable(CecapiDestinations.AI_ASSISTANT) {
            AiAssistantScreen()
        }
        composable(CecapiDestinations.DOCUMENT_READER) {
            DocumentReaderScreen()
        }
        composable(CecapiDestinations.REQUESTS) {
            RequestsScreen()
        }
        composable(CecapiDestinations.LEARNING) {
            LearningScreen()
        }
        composable(CecapiDestinations.ENVIRONMENT) {
            EnvironmentScreen()
        }
    }
}
