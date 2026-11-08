package org.greedycat.intelligence.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WorkspaceAmber = Color(0xFFDFBB7D)
val WorkspaceMuted = Color(0xFFA6B1BA)
val WorkspaceGreen = Color(0xFF88CCB2)

private val ResearchColors = darkColorScheme(
    primary = WorkspaceAmber,
    onPrimary = Color(0xFF211D16),
    primaryContainer = Color(0xFF3D3527),
    onPrimaryContainer = Color(0xFFF0D9B1),
    background = Color(0xFF101316),
    onBackground = Color(0xFFE5E7E8),
    surface = Color(0xFF191E23),
    onSurface = Color(0xFFE5E7E8),
    surfaceVariant = Color(0xFF272F35),
    onSurfaceVariant = WorkspaceMuted,
    outline = Color(0xFF53616B),
)

@Composable
fun ResearchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ResearchColors, content = content)
}
