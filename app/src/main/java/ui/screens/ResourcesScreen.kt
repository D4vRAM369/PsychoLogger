package com.d4vram.psychologger.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.psychologger.ui.components.WebCard

data class Resource(val title: String, val url: String)

@Composable
fun ResourcesScreen(
    resources: List<Resource> = listOf(
        Resource("MAPS","https://maps.org"),
        Resource("The Third Wave","https://thethirdwave.co"),
        Resource("Tripsit","https://tripsit.me"),
        Resource("Erowid","https://erowid.org"),
        Resource("PsychonautWiki","https://psychonautwiki.org")
    )
) {
    val ctx = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(resources) { res ->
            WebCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(res.url))
                        )
                    }
            ) {
                Text(text = res.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(text = res.url, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp))
            }
        }
    }
}

