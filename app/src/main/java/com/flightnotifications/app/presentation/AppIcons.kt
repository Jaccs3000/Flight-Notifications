package com.flightnotifications.app.presentation

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.flightnotifications.app.R

enum class AppIcon(@DrawableRes val resource: Int) {
    Flight(R.drawable.ic_ui_flight),
    Notifications(R.drawable.ic_ui_notifications),
    History(R.drawable.ic_ui_history),
    Settings(R.drawable.ic_ui_settings),
    Add(R.drawable.ic_ui_add),
    ArrowBack(R.drawable.ic_ui_arrow_back),
    ChevronRight(R.drawable.ic_ui_chevron_right),
    More(R.drawable.ic_ui_more),
    Calendar(R.drawable.ic_ui_calendar),
    Verified(R.drawable.ic_ui_verified),
    Clock(R.drawable.ic_ui_clock),
    Edit(R.drawable.ic_ui_edit),
    Delete(R.drawable.ic_ui_delete),
    Refresh(R.drawable.ic_ui_refresh),
    OpenExternal(R.drawable.ic_ui_open_external),
    Info(R.drawable.ic_ui_info),
    Key(R.drawable.ic_ui_key),
    Copy(R.drawable.ic_ui_copy),
    ExpandMore(R.drawable.ic_ui_expand_more),
    ExpandLess(R.drawable.ic_ui_expand_less),
    Shield(R.drawable.ic_ui_shield),
    List(R.drawable.ic_ui_list),
    Pause(R.drawable.ic_ui_pause),
    Location(R.drawable.ic_ui_location),
    Money(R.drawable.ic_ui_money),
}

@Composable
fun AppIcon(
    icon: AppIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        painter = painterResource(icon.resource),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun BackIconButton(onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            AppIcon(AppIcon.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

internal val AppIconSize = 24.dp
