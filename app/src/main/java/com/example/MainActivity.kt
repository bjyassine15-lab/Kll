package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.camera.NotebookVerificationScreen
import com.example.ui.camera.ScheduleCameraScreen
import com.example.ui.classmode.ClassListeningScreen
import com.example.ui.live.LiveAssistantDialog
import com.example.ui.main.MainChatScreen
import com.example.ui.memory.MemoryScreen
import com.example.ui.navigation.NavRoutes
import com.example.ui.settings.SettingsScreen
import com.example.ui.statistics.StatisticsScreen
import com.example.ui.teachers.TeacherEnrollmentScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.*

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val liveViewModel: LiveViewModel by viewModels()
    private val classViewModel: ClassViewModel by viewModels()
    private val memoryViewModel: MemoryViewModel by viewModels()
    private val enrollmentViewModel: TeacherEnrollmentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // Arabic-first RTL Layout Direction
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        StudyMindApp(
                            mainViewModel = mainViewModel,
                            liveViewModel = liveViewModel,
                            classViewModel = classViewModel,
                            memoryViewModel = memoryViewModel,
                            enrollmentViewModel = enrollmentViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudyMindApp(
    mainViewModel: MainViewModel,
    liveViewModel: LiveViewModel,
    classViewModel: ClassViewModel,
    memoryViewModel: MemoryViewModel,
    enrollmentViewModel: TeacherEnrollmentViewModel
) {
    val navController = rememberNavController()
    var showLiveDialog by remember { mutableStateOf(false) }

    // Request necessary runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled gracefully
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.MAIN_CHAT
    ) {
        composable(NavRoutes.MAIN_CHAT) {
            MainChatScreen(
                viewModel = mainViewModel,
                onOpenLiveAssistant = { showLiveDialog = true },
                onNavigateToClass = { navController.navigate(NavRoutes.CLASS_LISTENING) },
                onNavigateToScheduleCamera = { navController.navigate(NavRoutes.SCHEDULE_CAMERA) },
                onNavigateToNotebookVerify = { navController.navigate(NavRoutes.NOTEBOOK_VERIFY) },
                onNavigateToTeacherEnrollment = { navController.navigate(NavRoutes.TEACHER_ENROLLMENT) },
                onNavigateToMemory = { navController.navigate(NavRoutes.MEMORY) },
                onNavigateToStatistics = { navController.navigate(NavRoutes.STATISTICS) },
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) }
            )
        }

        composable(NavRoutes.CLASS_LISTENING) {
            ClassListeningScreen(
                viewModel = classViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToNotebookVerification = { navController.navigate(NavRoutes.NOTEBOOK_VERIFY) }
            )
        }

        composable(NavRoutes.TEACHER_ENROLLMENT) {
            TeacherEnrollmentScreen(
                memoryViewModel = memoryViewModel,
                enrollmentViewModel = enrollmentViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.MEMORY) {
            MemoryScreen(
                viewModel = memoryViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.STATISTICS) {
            StatisticsScreen(
                viewModel = memoryViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.SCHEDULE_CAMERA) {
            ScheduleCameraScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.NOTEBOOK_VERIFY) {
            NotebookVerificationScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }

    if (showLiveDialog) {
        LiveAssistantDialog(
            viewModel = liveViewModel,
            onDismiss = { showLiveDialog = false }
        )
    }
}
