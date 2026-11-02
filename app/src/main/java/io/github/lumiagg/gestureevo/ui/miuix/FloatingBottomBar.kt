package io.github.lumiagg.gestureevo.ui.miuix

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
data class FloatingBottomBarItem(val label: Int, val icon: Int)

private val defaultItems = listOf(
    FloatingBottomBarItem(R.string.ia_tab_overview, R.drawable.baseline_home_24),
    FloatingBottomBarItem(R.string.ia_tab_control, R.drawable.baseline_gesture_24),
    FloatingBottomBarItem(R.string.ia_tab_content, R.drawable.baseline_search_24),
    FloatingBottomBarItem(R.string.ia_tab_management, R.drawable.baseline_settings_24),
)

/** Reserves its real height through Scaffold; no visual offset or background blur. */
@Composable
fun FloatingBottomBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    floating: Boolean = true,
    modifier: Modifier = Modifier,
    items: List<FloatingBottomBarItem> = defaultItems,
) {
    require(items.isNotEmpty() && selectedIndex in items.indices)
    // Follow the actual app theme, not just the OS (the app supports forced light/dark).
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val container = animateColorAsState(
        if (dark) Color(0xFF1E1E1E) else Color.White, tween(300), label = "bar_background",
    ).value
    val border = animateColorAsState(
        if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFFE5E7EB), tween(300), label = "bar_border",
    ).value
    val shadow = if (dark) Color.Black.copy(alpha = 0.50f) else Color(0xFFE5E7EB)
    val shape = RoundedCornerShape(if (floating) 999.dp else 0.dp)
    Box(
        modifier = modifier.fillMaxWidth()
            .then(if (floating) Modifier else Modifier.background(container))
            .navigationBarsPadding()
            .padding(top = if (floating) 12.dp else 0.dp, bottom = if (floating) 24.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .then(if (floating) Modifier.widthIn(max = 440.dp).fillMaxWidth(0.9f) else Modifier.fillMaxWidth())
                .then(if (floating) Modifier.shadow(12.dp, shape, clip = false, ambientColor = shadow, spotColor = shadow) else Modifier)
                .background(container, shape)
                .then(if (floating) Modifier.border(1.dp, border, shape) else Modifier)
                .clip(shape)
                .selectableGroup()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = selectedIndex == index
                val color = animateColorAsState(
                    if (selected) {
                        if (dark) Color(0xFFC2C9F5) else Color(0xFF5A659C)
                    } else {
                        if (dark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                    }, tween(300), label = "bar_item_color",
                ).value
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onItemSelected(index) })
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Icon(ImageVector.vectorResource(item.icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    Text(stringResource(item.label), color = color, fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
    }
}
