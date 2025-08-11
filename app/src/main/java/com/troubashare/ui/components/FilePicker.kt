package com.troubashare.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun FilePickerButton(
    onFileSelected: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Upload File",
    enabled: Boolean = true
) {
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            val fileName = getFileName(context, selectedUri) ?: "unknown_file"
            onFileSelected(selectedUri, fileName)
        }
    }

    OutlinedButton(
        onClick = { 
            launcher.launch("*/*") // Accept all file types initially, we'll filter in the app
        },
        modifier = modifier,
        enabled = enabled
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun ImagePickerButton(
    onImageSelected: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Add Image",
    enabled: Boolean = true
) {
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            val fileName = getFileName(context, selectedUri) ?: "unknown_image.jpg"
            onImageSelected(selectedUri, fileName)
        }
    }

    OutlinedButton(
        onClick = { 
            launcher.launch("image/*")
        },
        modifier = modifier,
        enabled = enabled
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun PDFPickerButton(
    onPdfSelected: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Add PDF",
    enabled: Boolean = true
) {
    val context = LocalContext.current
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            val fileName = getFileName(context, selectedUri) ?: "unknown_document.pdf"
            onPdfSelected(selectedUri, fileName)
        }
    }

    OutlinedButton(
        onClick = { 
            launcher.launch("application/pdf")
        },
        modifier = modifier,
        enabled = enabled
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    } catch (e: Exception) {
        null
    }
}