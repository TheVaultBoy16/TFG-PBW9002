package com.example.myapplication.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    vmList: List<HomeItem>,
    onItemClick: (HomeItem) -> Unit,
    onToggleVm: (HomeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    if (vmList.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No se encontraron máquinas virtuales en este servidor.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vmList) { item ->
                HomeItemCard(
                    item = item,
                    onToggleClick = {
                        val stateLower = item.state.lowercase()
                        val isRunning = stateLower.contains("running") || stateLower.contains("ejecut")
                        if (!isRunning) {
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                        onToggleVm(item)
                    },
                    modifier = Modifier.clickable { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
fun HomeItemCard(
    item: HomeItem,
    onToggleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateLower = item.state.lowercase()
    val isRunning = stateLower.contains("running") || stateLower.contains("ejecut")
    val isSaved = stateLower.contains("saved") || stateLower.contains("guardada")
    
    val (displayState, stateColor) = when {
        isRunning -> "Ejecutándose" to Color(0xFF4CAF50)
        isSaved -> "Guardada" to Color(0xFF2196F3)
        else -> "Apagada" to Color.Gray
    }
    
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
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp)),
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
                    contentDescription = null,
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
