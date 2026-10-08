package io.legado.app.ui.widget.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.ConvertUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 源信息弹窗。
 *
 * 以类似笔记软件"笔记信息"的形式展示当前源的几个元数据：
 * 排序编号、最后更新时间、代码行数、整个源所占的文件大小。
 *
 * 由书源/订阅源编辑界面的溢出菜单调起，数据由调用方计算后传入，
 * 弹窗本身不依赖具体源类型。采用 DialogFragment + ComposeView 桥接到传统 View 体系。
 */
class SourceInfoDialog : DialogFragment() {

    /** 源名称，为空时不展示该行 */
    var sourceName: String = ""

    /** 排序编号（customOrder） */
    var customOrder: Int = 0

    /** 最后更新时间时间戳（毫秒），<=0 视为未更新 */
    var lastUpdateTime: Long = 0L

    /** 代码行数 */
    var codeLines: Int = 0

    /** 整个源所占的文件大小（字节） */
    var fileSize: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            LegadoTheme {
                SourceInfoContent(
                    sourceName = sourceName,
                    customOrder = customOrder,
                    lastUpdateTime = lastUpdateTime,
                    codeLines = codeLines,
                    fileSize = fileSize,
                    onDismiss = { dismissAllowingStateLoss() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 宿主窗口透明，卡片背景由 Compose Card 自绘，避免默认对话框边框叠加
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
        }
    }
}

@Composable
private fun SourceInfoContent(
    sourceName: String,
    customOrder: Int,
    lastUpdateTime: Long,
    codeLines: Int,
    fileSize: Long,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.source_info),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (sourceName.isNotBlank()) {
                InfoRow(stringResource(R.string.source_info_name), sourceName)
            }
            InfoRow(
                stringResource(R.string.source_info_order),
                customOrder.toString(),
            )
            InfoRow(
                stringResource(R.string.source_info_update_time),
                if (lastUpdateTime > 0) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(lastUpdateTime))
                } else {
                    stringResource(R.string.source_info_never)
                },
            )
            InfoRow(
                stringResource(R.string.source_info_code_lines),
                codeLines.toString(),
            )
            InfoRow(
                stringResource(R.string.source_info_file_size),
                ConvertUtils.formatFileSize(fileSize),
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.close),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.widthIn(max = 220.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}
