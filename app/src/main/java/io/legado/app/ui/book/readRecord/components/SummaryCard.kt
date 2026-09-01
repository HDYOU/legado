package io.legado.app.ui.book.readRecord.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.readRecord.readRecordCardBorder
import io.legado.app.ui.book.readRecord.readRecordSecondaryTextColor
import io.legado.app.ui.book.readRecord.readRecordSummaryCardContainerColor

@Composable
fun SummaryCard(
    totalReadTime: Long,
    todayReadTime: Long,
    monthReadTime: Long,
    activeDays: Int,
    currentStreak: Int,
    longestStreak: Int,
    bookCount: Int
) {
    val shape = RoundedCornerShape(16.dp)
    val isDarkBackground = MaterialTheme.colorScheme.background.luminance() < 0.18f
    val cardColor = if (isDarkBackground) {
        lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant,
            0.72f
        )
    } else {
        readRecordSummaryCardContainerColor()
    }
    val border = readRecordCardBorder()
    val secondaryTextColor = readRecordSecondaryTextColor()
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(if (isDarkBackground) 0.dp else 8.dp, shape, clip = false),
        shape = shape,
        color = cardColor,
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.rr_total_read_achievement),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryTextColor,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 主指标：累计时长是核心成就，大字独占一行，避免长数值在网格中折行 =====
            StatItem(
                label = stringResource(R.string.rr_total_read_time_label),
                value = formatDuration(context, totalReadTime),
                valueStyle = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 累计成就：值均为「x天/x本」短格式，4 列均分 =====
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    label = stringResource(R.string.rr_active_days),
                    value = stringResource(
                        if (activeDays == 1) R.string.rr_active_days_value_single
                        else R.string.rr_active_days_value,
                        activeDays
                    ),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.rr_books_read),
                    value = stringResource(
                        if (bookCount == 1) R.string.rr_book_count_value_single
                        else R.string.rr_book_count_value,
                        bookCount
                    ),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.rr_current_streak),
                    value = stringResource(
                        if (currentStreak == 1) R.string.rr_streak_value_single
                        else R.string.rr_streak_value,
                        currentStreak
                    ),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.rr_longest_streak),
                    value = stringResource(
                        if (longestStreak == 1) R.string.rr_streak_value_single
                        else R.string.rr_streak_value,
                        longestStreak
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ===== 近期时长：值可能达「xx小时xx分钟」，2 列布局预留宽度防止折行 =====
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    label = stringResource(R.string.rr_today_read_time),
                    value = formatDuration(context, todayReadTime),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.rr_month_read_time),
                    value = formatDuration(context, monthReadTime),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.titleMedium
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryTextColor = readRecordSecondaryTextColor()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = secondaryTextColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = valueStyle,
            color = primaryColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 时长格式化：x小时x分钟 / x小时 / x分钟 / x秒。
 * 小时不折算为天，累计阅读时长以总小时数展示（如 104小时30分钟），
 * 让「总时长」与「今日/本月时长」保持同一度量口径。
 */
private fun formatDuration(context: android.content.Context, mss: Long): String {
    val totalSeconds = mss / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> {
            val hourStr = context.getString(if (hours == 1L) R.string.rr_hour else R.string.rr_hours)
            if (minutes > 0) {
                val minStr = context.getString(if (minutes == 1L) R.string.rr_minute else R.string.rr_minutes)
                "$hours$hourStr$minutes$minStr"
            } else {
                "$hours$hourStr"
            }
        }
        minutes > 0 -> {
            val minStr = context.getString(if (minutes == 1L) R.string.rr_minute else R.string.rr_minutes)
            "$minutes$minStr"
        }
        else -> {
            val secStr = context.getString(if (seconds == 1L) R.string.rr_second else R.string.rr_seconds)
            "$seconds$secStr"
        }
    }
}
