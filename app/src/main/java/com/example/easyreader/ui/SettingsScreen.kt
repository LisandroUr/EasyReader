package com.example.easyreader.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LibraryViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showRestoreWarning by remember { mutableStateOf(false) }
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val workingMessage by viewModel.workingMessage.collectAsStateWithLifecycle()

    val syncInterval by viewModel.syncInterval.collectAsStateWithLifecycle()
    var expandedSyncInterval by remember { mutableStateOf(false) }
    val syncOptions = listOf(
        0 to "Manual",
        1 to "Cada hora",
        6 to "Cada 6 horas",
        24 to "Diariamente"
    )

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

    val allSeries by viewModel.allSeries.collectAsStateWithLifecycle()
    val allBooks by viewModel.allBooks.collectAsStateWithLifecycle()

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.restoreLibrary(it) { success ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        if (success) "Biblioteca restaurada con éxito"
                        else "Error al restaurar la biblioteca"
                    )
                }
            }
        }
    }

    val importSeriesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importSeries(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Working banner
            AnimatedVisibility(
                visible = isWorking,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = workingMessage ?: "Procesando...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Section 1: Appearance
            SettingsCard(title = "Apariencia") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tema de la aplicación",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                            onClick = { viewModel.setThemeMode("SYSTEM") },
                            selected = themeMode == "SYSTEM"
                        ) {
                            Text("Auto")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                            onClick = { viewModel.setThemeMode("LIGHT") },
                            selected = themeMode == "LIGHT"
                        ) {
                            Text("Claro")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                            onClick = { viewModel.setThemeMode("DARK") },
                            selected = themeMode == "DARK"
                        ) {
                            Text("Oscuro")
                        }
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                            onClick = { viewModel.setThemeMode("AMOLED") },
                            selected = themeMode == "AMOLED"
                        ) {
                            Text("OLED")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Color Dinámico (Material You)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Adapta la paleta al fondo de pantalla del sistema",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }
                }
            }

            // Section 2: Sync & Folders
            SettingsCard(title = "Sincronización y Almacenamiento") {
                Column(modifier = Modifier.padding(16.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedSyncInterval,
                        onExpandedChange = { expandedSyncInterval = !expandedSyncInterval },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = syncOptions.find { it.first == syncInterval }?.second ?: "Desconocido",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Frecuencia de sincronización") },
                            leadingIcon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSyncInterval) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            shape = MaterialTheme.shapes.small
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSyncInterval,
                            onDismissRequest = { expandedSyncInterval = false }
                        ) {
                            syncOptions.forEach { (hours, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.setSyncInterval(hours)
                                        expandedSyncInterval = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { viewModel.rescanAllBoundFolders() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isWorking,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Outlined.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sincronizar carpetas ahora", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Section 3: Backup & Restore
            SettingsCard(title = "Copia de Seguridad y Restauración") {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text("Crear copia de seguridad (.zip)", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Exporta la base de datos, portadas y avance de lectura a un archivo zip", style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = {
                            FilledTonalIconButton(onClick = {
                                viewModel.backupLibrary { uri ->
                                    uri?.let {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_STREAM, it)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Guardar copia de seguridad"))
                                    }
                                }
                            }) {
                                Icon(Icons.Outlined.Backup, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                viewModel.backupLibrary { uri ->
                                    uri?.let {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_STREAM, it)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Guardar copia de seguridad"))
                                    }
                                }
                            }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    ListItem(
                        headlineContent = { Text("Restaurar copia de seguridad", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Reemplaza la biblioteca actual con un respaldo previo", style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = {
                            FilledTonalIconButton(
                                onClick = { showRestoreWarning = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Outlined.Restore, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { showRestoreWarning = true }
                    )
                }
            }

            // Section 4: Community Sharing
            SettingsCard(title = "Compartición de Series") {
                Column(modifier = Modifier.padding(8.dp)) {
                    ListItem(
                        headlineContent = { Text("Importar paquete .easyreader", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Importa una serie completa compartida por otro usuario", style = MaterialTheme.typography.bodyMedium) },
                        leadingContent = {
                            FilledTonalIconButton(onClick = { importSeriesLauncher.launch(arrayOf("*/*")) }) {
                                Icon(Icons.Outlined.Share, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { importSeriesLauncher.launch(arrayOf("*/*")) }
                    )
                }
            }

            // Section 5: Stats & About
            SettingsCard(title = "Información de la Biblioteca") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${allSeries.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Series",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${allBooks.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Libros PDF",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "1.0",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Versión",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Restore Confirmation Dialog
        if (showRestoreWarning) {
            AlertDialog(
                onDismissRequest = { showRestoreWarning = false },
                containerColor = MaterialTheme.colorScheme.surface,
                icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = {
                    Text(
                        "¿Sobrescribir biblioteca?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        "Restaurar desde una copia de seguridad reemplazará permanentemente la biblioteca y el progreso actual por los del archivo de respaldo.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRestoreWarning = false
                            restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Seleccionar archivo y restaurar", style = MaterialTheme.typography.labelLarge)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreWarning = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}
