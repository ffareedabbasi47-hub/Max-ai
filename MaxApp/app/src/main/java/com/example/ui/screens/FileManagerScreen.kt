package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.NoteEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.MaxViewModel

@Composable
fun FileManagerScreen(
    viewModel: MaxViewModel,
    modifier: Modifier = Modifier
) {
    val notes by viewModel.notesList.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var inputTitle by remember { mutableStateOf("") }
    var inputContent by remember { mutableStateOf("") }
    var inputFileType by remember { mutableStateOf("TXT") }
    var inputFolder by remember { mutableStateOf("General") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DOCUMENT & FILE VAULT",
                color = CyanPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create File", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "New File", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "STORAGE INDEX (${notes.size} FILES)",
            color = TextCyanMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(notes) { note ->
                FileItemCard(
                    note = note,
                    onDelete = { viewModel.deleteNote(note.id) }
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "Create Document File",
                    color = CyanPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputTitle,
                        onValueChange = { inputTitle = it },
                        label = { Text("File Title / Name", color = TextCyanMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextCyanLight,
                            unfocusedTextColor = TextCyanLight
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = inputFileType == "TXT",
                            onClick = { inputFileType = "TXT" },
                            label = { Text(".TXT") }
                        )
                        FilterChip(
                            selected = inputFileType == "DOCX",
                            onClick = { inputFileType = "DOCX" },
                            label = { Text(".DOCX") }
                        )
                        FilterChip(
                            selected = inputFileType == "PDF",
                            onClick = { inputFileType = "PDF" },
                            label = { Text(".PDF") }
                        )
                    }

                    OutlinedTextField(
                        value = inputFolder,
                        onValueChange = { inputFolder = it },
                        label = { Text("Folder Name", color = TextCyanMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextCyanLight,
                            unfocusedTextColor = TextCyanLight
                        )
                    )

                    OutlinedTextField(
                        value = inputContent,
                        onValueChange = { inputContent = it },
                        label = { Text("Document Content", color = TextCyanMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextCyanLight,
                            unfocusedTextColor = TextCyanLight
                        ),
                        modifier = Modifier.height(120.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputTitle.isNotBlank()) {
                            viewModel.createNote(inputTitle, inputContent, inputFileType, inputFolder)
                            showCreateDialog = false
                            inputTitle = ""
                            inputContent = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                ) {
                    Text("Save File", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextCyanMuted)
                }
            },
            containerColor = HudSurface
        )
    }
}

@Composable
private fun FileItemCard(
    note: NoteEntity,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HudSurface, shape = RoundedCornerShape(12.dp))
            .border(1.dp, HudBorderCyan, shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Doc",
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${note.title}.${note.fileType.lowercase()}",
                        color = TextCyanLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder",
                        tint = CyanSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = note.folder,
                        color = TextCyanMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = NeonRedError,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onDelete() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = note.content,
                color = TextCyanMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 3
            )
        }
    }
}
