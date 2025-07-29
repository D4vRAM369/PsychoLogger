package com.d4vram.psychologger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class Resource(val title: String, val url: String)

@Composable
fun ResourcesScreen(
    resources: List<Resource> = listOf(
        Resource("Erowid", "https://erowid.org"),
        Resource("PsychonautWiki", "https://psychonautwiki.org")
    )
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(resources) { res ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable {
                        // TODO: aquí lanzar Intent.ACTION_VIEW con res.url
                    },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Text(
                    text = res.title,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
