package com.flightnotifications.app.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightnotifications.app.ui.theme.FlightAppColors

@Composable
internal fun SketchSectionHeader(title: String, icon: AppIcon, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(FlightAppColors.mintHeader)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(color = FlightAppColors.mintIcon, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(icon, null, modifier = Modifier.size(20.dp), tint = FlightAppColors.tealDark)
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = FlightAppColors.ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun FlightSkyDecoration(modifier: Modifier = Modifier, hero: Boolean = false) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val faintWhite = Color.White.copy(alpha = if (hero) 0.12f else 0.07f)
            drawCircle(
                color = faintWhite,
                radius = size.minDimension * if (hero) 0.50f else 0.40f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.93f, size.height * 0.18f),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            if (hero) {
                val cloud = Color.White.copy(alpha = 0.78f)
                drawCircle(cloud, size.minDimension * 0.12f, androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.74f))
                drawCircle(cloud, size.minDimension * 0.17f, androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.67f))
                drawCircle(cloud, size.minDimension * 0.13f, androidx.compose.ui.geometry.Offset(size.width * 0.91f, size.height * 0.74f))
                drawCircle(Color(0xFFFFD166).copy(alpha = 0.85f), size.minDimension * 0.10f, androidx.compose.ui.geometry.Offset(size.width * 0.98f, size.height * 0.21f))
            }
        }
        AppIcon(
            AppIcon.Flight,
            null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = if (hero) 42.dp else 24.dp, top = if (hero) 7.dp else 8.dp)
                .size(if (hero) 112.dp else 126.dp)
                .rotate(-20f),
            tint = Color.White.copy(alpha = if (hero) 0.95f else 0.12f),
        )
        if (hero) {
            Text("✦", modifier = Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 8.dp), color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp)
        }
    }
}

@Composable
internal fun SketchSurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 3.dp,
        content = content,
    )
}
