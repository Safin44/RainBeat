package com.rainbeat

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.rainbeat.ui.RainBeatApp
import com.rainbeat.ui.SharedViewModel
import com.rainbeat.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var viewModelReference: SharedViewModel? = null

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModelReference?.onIntentSenderResult(result.resultCode == RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RainBeatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: SharedViewModel = hiltViewModel()
                    viewModelReference = viewModel
                    
                    val pendingIntentRequest by viewModel.pendingIntentRequest.collectAsState()
                    
                    LaunchedEffect(pendingIntentRequest) {
                        pendingIntentRequest?.let { 
                            intentSenderLauncher.launch(it)
                        }
                    }

                    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        listOf(
                            android.Manifest.permission.READ_MEDIA_AUDIO,
                            android.Manifest.permission.READ_MEDIA_VIDEO
                        )
                    } else {
                        listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }

                    val permissionsState = rememberMultiplePermissionsState(permissions)

                    if (permissionsState.allPermissionsGranted) {
                        viewModel.onPermissionsGranted()
                        RainBeatApp(viewModel = viewModel)
                        
                        LaunchedEffect(intent) {
                            if (intent?.action == Intent.ACTION_VIEW) {
                                val uri = intent?.data
                                if (uri != null) {
                                    val mimeType = intent?.type ?: contentResolver.getType(uri) ?: ""
                                    val isVideo = mimeType.startsWith("video")
                                    viewModel.playExternalUri(uri, isVideo, this@MainActivity)
                                    // Consume intent so it doesn't re-trigger on recreation
                                    intent.action = Intent.ACTION_MAIN
                                }
                            }
                        }
                    } else {
                        PermissionRationaleScreen(permissionsState)
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isVideoPlaying = viewModelReference?.videoPlayerController?.isPlaying?.value == true
            val isVideoScreen = viewModelReference?.isInVideoPlayerScreen?.value == true
            if (isVideoPlaying && isVideoScreen) {
                enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRationaleScreen(permissionsState: MultiplePermissionsState) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.FolderSpecial,
            contentDescription = "Storage",
            tint = NeonCyan,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Storage Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "RainBeat needs access to your local media to play your music and videos. We do not access any other personal files.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (permissionsState.shouldShowRationale) {
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("Grant Permission", color = BlackBackground)
            }
        } else if (permissionsState.revokedPermissions.isNotEmpty() && !permissionsState.shouldShowRationale) {
            // First time, or permanently denied.
            // If it's first time, we just show the request button.
            // But accompanist handles this tricky state. We show a button that either requests or goes to settings.
            Button(
                onClick = {
                    if (permissionsState.revokedPermissions.isNotEmpty()) {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                    // If the user permanently denied, launchMultiplePermissionRequest() might do nothing.
                    // We can provide a button to go to settings explicitly.
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("Grant Permission", color = BlackBackground)
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("Open App Settings", color = NeonCyan)
            }
        }
    }
}
