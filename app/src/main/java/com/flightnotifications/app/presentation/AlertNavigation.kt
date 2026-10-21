package com.flightnotifications.app.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class MainTab(val label: String, val icon: AppIcon, val accent: Color, val background: Color) {
    ALERTS("Alertas", AppIcon.Notifications, Color(0xFF087D78), Color(0xFFD9F5EE)),
    ACTIVITY("Actividad", AppIcon.History, Color(0xFF2258BB), Color(0xFFE5EEFF)),
    SETTINGS("Ajustes", AppIcon.Settings, Color(0xFF7543B8), Color(0xFFF0E7FC)),
}

@Composable
internal fun FlightBottomNavigation(selected: MainTab, onSelect: (MainTab) -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 8.dp,
    ) {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            MainTab.entries.forEach { tab ->
                val isSelected = selected == tab
                NavigationBarItem(
                    modifier = Modifier.testTag("nav-${tab.name}"),
                    selected = isSelected,
                    onClick = { onSelect(tab) },
                    icon = {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            color = if (isSelected) Color.Transparent else tab.background,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                AppIcon(tab.icon, null, Modifier.size(24.dp), tint = tab.accent)
                            }
                        }
                    },
                    label = { Text(tab.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = tab.accent,
                        unselectedIconColor = tab.accent,
                        selectedTextColor = tab.accent,
                        unselectedTextColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = tab.background,
                    ),
                )
            }
        }
    }
}

internal enum class AlertListFilter(
    val label: String,
    val accent: Color,
    val background: Color,
    val selectedBackground: Color,
    val counterBackground: Color,
) {
    ACTIVE("Activas", Color(0xFF176D5C), Color(0xFFEFF9F5), Color(0xFFD9F4EB), Color(0xFFAEECD3)),
    INACTIVE("Inactivas", Color(0xFF8A5717), Color(0xFFFBF7EE), Color(0xFFFFF0D8), Color(0xFFF2E1C9)),
    ALL("Todas", Color(0xFF244EBA), Color(0xFFF0F2FE), Color(0xFFE7EDFF), Color(0xFFCAD8F6)),
}

@Composable
internal fun AlertStatusFilters(
    selected: AlertListFilter,
    activeCount: Int,
    inactiveCount: Int,
    totalCount: Int,
    onSelect: (AlertListFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(15.dp),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup().padding(start = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlertListFilter.entries.forEach { filter ->
                if (filter != AlertListFilter.ACTIVE) {
                    Box(Modifier.width(if (filter == AlertListFilter.ALL) 10.dp else 8.dp).height(48.dp), contentAlignment = Alignment.Center) {
                        if (filter == AlertListFilter.ALL) {
                            Box(Modifier.width(1.dp).height(28.dp).background(Color(0xFFF0F0EE)))
                        }
                    }
                }
                val isSelected = selected == filter
                val count = when (filter) {
                    AlertListFilter.ACTIVE -> activeCount
                    AlertListFilter.INACTIVE -> inactiveCount
                    AlertListFilter.ALL -> totalCount
                }
                Box(
                    modifier = Modifier.weight(if (filter == AlertListFilter.ACTIVE) 1.04f else 1f).heightIn(min = 48.dp)
                        .testTag("filter-${filter.name}")
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(filter) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        color = if (isSelected) filter.selectedBackground else filter.background,
                        shape = RoundedCornerShape(12.dp),
                        border = if (filter == AlertListFilter.ALL) BorderStroke(1.dp, Color(0xFFE5E8F2)) else null,
                    ) {
                        BoxWithConstraints {
                            val compact = maxWidth < 104.dp || count >= 100
                            val textSize = if (compact) 12.sp else 13.sp
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp)
                                    .padding(start = if (compact) 8.dp else 12.dp, end = if (compact) 4.dp else 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    filter.label,
                                    modifier = Modifier.weight(1f),
                                    color = filter.accent,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = textSize, lineHeight = 18.sp),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier.heightIn(min = 21.dp).widthIn(min = 21.dp)
                                        .background(filter.counterBackground, CircleShape)
                                        .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        count.toString(),
                                        color = filter.accent,
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = textSize, lineHeight = 17.sp),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
