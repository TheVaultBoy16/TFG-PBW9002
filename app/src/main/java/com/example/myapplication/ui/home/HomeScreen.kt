package com.example.myapplication.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun HomeScreen(
    vmList: List<HomeItem>,
    onItemClick: (HomeItem) -> Unit,
    onToggleVm: (HomeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(vmList) { item ->
            HomeItemCard(
                item = item,
                onToggleClick = { onToggleVm(item) },
                modifier = Modifier.clickable { onItemClick(item) }
            )
        }
    }
}

@Composable
fun HomeItemCard(
    item: HomeItem,
    onToggleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = item.state.lowercase().contains("running")
    val displayState = if (isRunning) "Ejecutándose" else "Apagada"
    val stateColor = if (isRunning) Color(0xFF4CAF50) else Color.Gray
    
    // Seleccionamos la imagen según el estado
    val imageRes = if (isRunning) R.drawable.ejecutandose else R.drawable.apagada

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp) // Tamaño cuadrado aumentado
                    .clip(RoundedCornerShape(8.dp)), // Bordes suavizados para coincidir con la card
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = displayState,
                    style = MaterialTheme.typography.bodySmall,
                    color = stateColor
                )
            }

            IconButton(onClick = onToggleClick) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) "Acción" else "",
                    tint = if (isRunning) Color(0xFFF44336) else Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(vmList = emptyList(), onItemClick = {}, onToggleVm = {})
}
