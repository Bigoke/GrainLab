package com.bigoke.grainlab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Three short pages shown on first launch. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        Triple(Icons.Filled.CameraAlt, "Shoot on film", "Over 60 film looks from Kodak, Fujifilm, Agfa, Ilford, Polaroid and more, live in the camera. Swipe on the picture to change looks."),
        Triple(Icons.Filled.Tune, "Edit like a pro", "Auto fix, curves, masks for sky and subject, healing, texture, clarity and dehaze. Tap the photo for full screen, pinch to zoom."),
        Triple(Icons.Filled.Bookmarks, "Make it yours", "Save any edit as a template and shoot with it. Share templates as a QR code. Everything stays on your phone."),
    )
    var page by remember { mutableIntStateOf(0) }
    val (icon, title, body) = pages[page]
    Column(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Skip", color = Muted) }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(120.dp).background(Color(0x22FFB347), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Amber, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, color = Muted, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.indices.forEach { i ->
                Box(Modifier.size(if (i == page) 10.dp else 8.dp).background(if (i == page) Amber else Color(0x55FFFFFF), CircleShape))
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { if (page < pages.lastIndex) page++ else onDone() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (page < pages.lastIndex) "Next" else "Start shooting")
        }
    }
}
