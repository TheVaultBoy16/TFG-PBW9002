package com.example.myapplication.ui.vm

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.data.HomeItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmScreen(
    item: HomeItem,
    snapshotList: List<String>,
    onTakeSnapshot: (String) -> Unit,
    onRestoreSnapshot: (String) -> Unit,
    onDeleteSnapshot: (String) -> Unit,
    onSaveVm: () -> Unit,
    onRestoreVm: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newSnapshotName by remember { mutableStateOf("") }
    var selectedSnapshot by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val isRunning = item.state.lowercase().contains("running")
    val displayState = if (isRunning) "Ejecutándose" else "Apagada"
    val stateColor = if (isRunning) Color(0xFF4CAF50) else Color.Gray
    val imageRes = if (isRunning) R.drawable.ejecutandose else R.drawable.apagada

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = "VM Image",
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = item.name, style = MaterialTheme.typography.headlineSmall)
                Text(text = displayState, style = MaterialTheme.typography.bodyMedium, color = stateColor)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sección de tomar instantánea
        Text(text = "Crear Instantánea", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newSnapshotName,
                onValueChange = { newSnapshotName = it },
                label = { Text("Nombre") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = { onTakeSnapshot(newSnapshotName); newSnapshotName = "" },
                enabled = newSnapshotName.isNotEmpty()
            ) {
                Text("Tomar")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sección restauración y borrado de instantáneas (lista de instantáneas)
        Text(text = "Gestionar Instantáneas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = if (selectedSnapshot.isEmpty()) "Selecciona una instantánea" else selectedSnapshot,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                snapshotList.forEach { snapshot ->
                    DropdownMenuItem(
                        text = { Text(snapshot) },
                        onClick = {
                            selectedSnapshot = snapshot
                            expanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = { onRestoreSnapshot(selectedSnapshot) },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedSnapshot.isNotEmpty()
        ) {
            Text("Restaurar Seleccionada")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onDeleteSnapshot(selectedSnapshot); selectedSnapshot = "" },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedSnapshot.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Borrar Seleccionada")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sección gestión de memoria (Save/Restore)
        Text(text = "Gestión de Memoria", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onSaveVm, modifier = Modifier.weight(1f), enabled = isRunning) {
                Text("Guardar")
            }
            Button(onClick = onRestoreVm, modifier = Modifier.weight(1f), enabled = !isRunning) {
                Text("Cargar")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VmScreenPreview() {
    VmScreen(
        item = HomeItem("1", "MV de Prueba", "running", R.drawable.apagada),
        snapshotList = listOf("Snap1", "Snap2"),
        onTakeSnapshot = {},
        onRestoreSnapshot = {},
        onDeleteSnapshot = {},
        onSaveVm = {},
        onRestoreVm = {}
    )
}
