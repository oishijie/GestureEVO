package io.github.lumiagg.gestureevo.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lumiagg.gestureevo.R

/**
 * 二级页共享组件层（《UI 设计规范》4.x 的 Compose 实现）：
 * [SectionHeader] 组标题（卡外）、[SectionCard] 分组卡 + 行、[StatusCard] 主角状态卡。
 * 与 View 侧 SettingsSectionCard/SettingsNavRow 保持同一观感与密度。
 */

private val CardShape = RoundedCornerShape(16.dp)
private val HeroShape = RoundedCornerShape(24.dp)

/** 分组列表行描述。[showChevron] 为 false 时该行仅作展示（如关于页的维护者）。 */
@Immutable
data class RowSpec(
    val iconRes: Int,
    val title: String,
    val subtitle: String? = null,
    val showChevron: Boolean = true,
    val onClick: () -> Unit = {},
)

/** 组标题：14sp / 600 / primary，卡外。 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
    )
}

/** 分组卡：surfaceBright 底、16dp 圆角、无描边；行间 1dp 分隔线（起点 56dp）。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    rows: List<RowSpec>,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                SectionRow(row)
            }
        }
    }
}

@Composable
private fun SectionRow(row: RowSpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onClick)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(row.iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            row.subtitle?.let { subtitle ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (row.showChevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 主角状态卡：primary→tertiary 渐变底、24dp 圆角；label + 大号 value +
 * 可选 summary + 可选「去处理」按钮。整卡可点。
 */
@Composable
fun StatusCard(
    label: String,
    value: String,
    summary: String? = null,
    actionLabel: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = HeroShape,
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))
                )
                .padding(20.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
            )
            summary?.let { text ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            if (!actionLabel.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onClick) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** 便捷函数：从资源构建 [StatusCard] 文案。 */
@Composable
fun StatusCard(
    labelRes: Int,
    value: String,
    summaryRes: Int? = null,
    actionRes: Int? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    StatusCard(
        label = stringResource(labelRes),
        value = value,
        summary = summaryRes?.let { stringResource(it) },
        actionLabel = actionRes?.let { stringResource(it) },
        onClick = onClick,
        modifier = modifier,
    )
}

/** 通用空隙：分组间距 8dp 的语义别名。 */
val SectionSpacing = Arrangement.spacedBy(8.dp)
