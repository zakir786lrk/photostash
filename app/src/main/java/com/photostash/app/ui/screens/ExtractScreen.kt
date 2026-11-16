package com.photostash.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photostash.app.core.EmbeddedEntry
import com.photostash.app.core.FileUtils
import com.photostash.app.core.PolyglotExtractor
import com.photostash.app.ui.components.AppHeader
import com.photostash.app.ui.theme.AccentGreen
import com.photostash.app.ui.theme.DarkBackground
import com.photostash.app.ui.theme.DarkCard
import com.photostash.app.ui.theme.DarkCardBorder
import com.photostash.app.ui.theme.DarkSurface
import com.photostash.app.ui.theme.TextMuted
import com.photostash.app.ui.theme.TextPrimary
import com.photostash.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ExtractScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageName by remember { mutableStateOf("") }
    val embeddedEntries = remember { mutableStateListOf<EmbeddedEntry>() }

    var isInspecting by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableFloatStateOf(0f) }
    var extractStatus by remember { mutableStateOf("") }
    var extractedCount by remember { mutableStateOf(0) }
    var inspectionAttempted by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            val info = FileUtils.resolveFileInfo(context, uri)
            selectedImageName = info.name
            embeddedEntries.clear()
            inspectionAttempted = false
            extractedCount = 0

            isInspecting = true
            scope.launch {
                try {
                    val entries = PolyglotExtractor.inspectEmbeddedFiles(context, uri)
                    embeddedEntries.addAll(entries)
                    inspectionAttempted = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isInspecting = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        AppHeader(
            title = "Extract Files",
            subtitle = "Inspect and recover files hidden inside a PNG image"
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Image Selector
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(DarkCard, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = AccentGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (selectedImageUri == null) "Select Image" else selectedImageName,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (selectedImageUri == null) "Pick a PNG downloaded from Google Photos" else "Ready to extract",
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (selectedImageUri == null) "Choose Image to Inspect" else "Change Image",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Section 2: Loading State
            if (isInspecting) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Scanning image for embedded files...", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }

            // Section 3: Embedded Files List
            if (inspectionAttempted) {
                if (embeddedEntries.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "⚠️ No Hidden Files Found",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "This image does not appear to contain an embedded ZIP payload. Make sure it was downloaded in Original Quality and not re-compressed.",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "HIDDEN FILES (${embeddedEntries.size})",
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = FileUtils.formatFileSize(embeddedEntries.sumOf { it.uncompressedSize }),
                                color = AccentGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            embeddedEntries.forEach { entry ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DarkSurface)
                                        .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(DarkCard, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = entry.extension,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = AccentGreen
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = entry.formattedSize,
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Success feedback
            if (extractedCount > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Done, contentDescription = null, tint = AccentGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Extracted $extractedCount files!",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "All files have been restored to your Downloads/PhotoStash folder.",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            if (isExtracting) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { extractProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentGreen,
                            trackColor = DarkCard
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = extractStatus,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Action Button: "Extract All"
        if (embeddedEntries.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Button(
                    onClick = {
                        val uri = selectedImageUri ?: return@Button
                        isExtracting = true
                        scope.launch {
                            try {
                                val uris = PolyglotExtractor.extractAllFiles(context, uri) { progress, status ->
                                    extractProgress = progress
                                    extractStatus = status
                                }
                                extractedCount = uris.size
                                Toast.makeText(context, "Extracted ${uris.size} files to Downloads!", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Extraction error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExtracting = false
                            }
                        }
                    },
                    enabled = !isExtracting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        disabledContainerColor = DarkCard
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(
                            color = DarkBackground,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = DarkBackground)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Extract All to Downloads",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkBackground
                        )
                    }
                }
            }
        }
    }
}
