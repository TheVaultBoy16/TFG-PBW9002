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

@Composable
fun VmScreen(
    item: HomeItem,
    onTakeSnapshot: (String) -> Unit,
    onRestoreSnapshot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var snapshotName by remember { mutableStateOf("") }


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
                Text(
                    text = item.name, 
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = displayState, 
                    style = MaterialTheme.typography.bodyMedium,
                    color = stateColor // Aplicamos el color correspondiente
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = snapshotName,
            onValueChange = { snapshotName = it },
            label = { Text("Nombre de la instantánea") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { if (snapshotName.isNotEmpty()) onTakeSnapshot(snapshotName) },
                modifier = Modifier.weight(1f),
                enabled = snapshotName.isNotEmpty()
            ) {
                Text("Tomar")
            }

            Button(
                onClick = { if (snapshotName.isNotEmpty()) onRestoreSnapshot(snapshotName) },
                modifier = Modifier.weight(1f),
                enabled = snapshotName.isNotEmpty()
            ) {
                Text("Restaurar")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VmScreenPreview() {
    VmScreen(
        item = HomeItem("1", "MV de Prueba", "running", R.drawable.ic_launcher_foreground),
        onTakeSnapshot = {},
        onRestoreSnapshot = {}
    )
}
