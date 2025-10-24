package com.troubashare.ui.cloud

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.troubashare.ui.theme.TroubaShareTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CloudAuthActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_PROVIDER = "provider"
        const val RESULT_SUCCESS = "success"
        const val RESULT_ERROR = "error"
        private const val TAG = "CloudAuthActivity"
    }
    
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSignInResult(result.data)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val provider = intent.getStringExtra(EXTRA_PROVIDER) ?: "google_drive"
        
        setContent {
            TroubaShareTheme {
                CloudAuthScreen(
                    provider = provider,
                    onSignIn = { startGoogleSignIn() },
                    onCancel = { finish() }
                )
            }
        }
    }
    
    private fun startGoogleSignIn() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE))
                .build()
            
            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Google Sign In", e)
            returnResult(false, e.message ?: "Unknown error")
        }
    }
    
    private fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            Log.d(TAG, "Google Sign In successful: ${account.email}")
            Log.d(TAG, "Account granted scopes: ${account.grantedScopes}")
            
            // Verify the account is actually stored
            val storedAccount = GoogleSignIn.getLastSignedInAccount(this)
            Log.d(TAG, "Stored account after sign-in: ${storedAccount?.email}")
            
            returnResult(true, "Successfully signed in to Google Drive")
            
        } catch (e: ApiException) {
            Log.e(TAG, "Google Sign In failed with code: ${e.statusCode}", e)
            returnResult(false, "Sign in failed: ${e.message}")
        }
    }
    
    private fun returnResult(success: Boolean, message: String) {
        val resultIntent = Intent().apply {
            putExtra(RESULT_SUCCESS, success)
            putExtra(RESULT_ERROR, message)
        }
        setResult(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED, resultIntent)
        finish()
    }
}

@Composable
fun CloudAuthScreen(
    provider: String,
    onSignIn: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Google Drive",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Connect to Google Drive",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Enable cloud synchronization to collaborate with your band members. Your files will be stored securely in your Google Drive.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                isLoading = true
                onSignIn()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Signing in...")
            } else {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Google")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Privacy & Security",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• Your files remain encrypted and private\n" +
                          "• Only you and your band members can access shared content\n" +
                          "• You can revoke access anytime from Google Drive settings\n" +
                          "• TroubaShare works fully offline when needed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}