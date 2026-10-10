package io.legado.app.ui.widget.components.dialog

import io.legado.app.lib.theme.eInkGrayscale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageTopBarColors

/**
 * 澶氶€夐」鐩暟鎹ā鍨?
 */
data class MultiSelectItem(
    val key: String, // 鍞竴鏍囪瘑
    val title: String, // 涓绘爣棰?
    val subtitle: String? = null, // 鍓爣棰?(濡傛枃浠跺悕)
    val size: String? = null, // 澶у皬淇℃伅 (濡?"2.5 MB")
    val rawSize: Long? = null,
    val count: String? = null, // 鏁伴噺淇℃伅 (濡?"128 涓?)
    val group: String, // 鍒嗙粍鍚嶇О
    val iconEmoji: String? = null, // Emoji鍥炬爣 (濡?"馃摎")
    val selected: Boolean = true, // 鏄惁閫変腑
)

/**
 * 鍒嗙粍鏁版嵁妯″瀷
 */
data class MultiSelectGroup(
    val name: String, // 鍒嗙粍鍚嶇О
    val iconEmoji: String? = null, // 鍒嗙粍鍥炬爣
    val items: List<MultiSelectItem>, // 璇ュ垎缁勭殑椤圭洰
)

/**
 * 閫氱敤鐨勫閫夊璇濇缁勪欢
 *
 * 鍔熻兘鐗规€?
 * - 鍒嗙粍灞曠ず椤圭洰
 * - 鏄剧ず澶氱淇℃伅(鏍囬銆佸壇鏍囬銆佸ぇ灏忋€佹暟閲忋€佸浘鏍?
 * - 瀹炴椂璁＄畻閫変腑椤规€诲ぇ灏?
 * - 鍏ㄩ€?鍏ㄤ笉閫夊揩鎹锋寜閽?
 * - 涓婚閫傞厤
 * - 鍙€夋彃妲? 鏍囬鏍忓姩浣滄寜閽€佽灏惧唴瀹广€佺嫭绔嬬殑纭畾鍥炶皟(濡?寮€濮嬫仮澶?)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MultiSelectDialogContent(
    title: String, // 瀵硅瘽妗嗘爣棰?
    description: String? = null, // 鎻忚堪鏂囧瓧
    groups: List<MultiSelectGroup>, // 鍒嗙粍鏁版嵁
    selectedKeys: Set<String>, // 宸查€変腑鐨刱ey闆嗗悎
    totalSizeCalculator: (List<MultiSelectItem>) -> String?, // 鎬诲ぇ灏忚绠楀櫒
    onSelectionChange: (String, Boolean) -> Unit, // 閫夋嫨鍙樺寲鍥炶皟
    onDismiss: () -> Unit, // 鍏抽棴鍥炶皟
    onSelectAll: () -> Unit, // 鍏ㄩ€夊洖璋?
    onDeselectAll: () -> Unit, // 鍏ㄤ笉閫夊洖璋?
    headerAction: (@Composable () -> Unit)? = null, // 鏍囬鏍忓姩浣滄寜閽彃妲?(濡?妫€娴嬫牸寮?)
    itemTrailing: (@Composable (MultiSelectItem) -> Unit)? = null, // 琛屽熬鍐呭鎻掓Ы (濡傞獙璇佺姸鎬佸浘鏍?
    headerContent: (@Composable () -> Unit)? = null,
    onConfirm: (() -> Unit)? = null, // 纭畾鎸夐挳鍥炶皟, 涓虹┖鏃剁‘瀹氭寜閽粎鍏抽棴寮圭獥
) {
    val topBarColor = pageTopBarColors().containerColor
    val cardColor = pageCardContainerColor()

    // 璁＄畻閫変腑椤?
    val selectedItems = remember(groups, selectedKeys) {
        groups.flatMap { it.items }.filter { it.key in selectedKeys }
    }

    // 璁＄畻鎬诲ぇ灏?
    val totalSize = remember(selectedItems) {
        totalSizeCalculator(selectedItems)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        // 弹层是独立窗口，页面那层灰阶罩不到，这里再贴一次
        BoxWithConstraints(Modifier.fillMaxWidth().eInkGrayscale()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(max = maxHeight * 0.92f),
                shape = MaterialTheme.shapes.large,
                color = cardColor,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // 鏍囬鏍?
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(topBarColor)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        // 宸︿晶锛氭爣棰樺拰鎻忚堪
                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // 鍙充晶锛氭湁鍔ㄤ綔鎸夐挳鏃舵斁鎸夐挳锛堝凡閫夋暟閲忎笅绉诲埌搴曢儴锛夛紝鍚﹀垯鏄剧ず宸查€夋暟閲?
                        if (headerAction != null) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .align(Alignment.CenterVertically),
                            ) {
                                headerAction()
                            }
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.multi_select_selected_count,
                                    selectedItems.size,
                                    groups.sumOf { it.items.size },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }

                    // 椤圭洰鍒楄〃
                    headerContent?.invoke()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        // 鍒嗙粍灞曠ず (鍒嗙粍鍚嶄负绌烘椂鎸夊钩閾哄垪琛ㄥ鐞? 涓嶆覆鏌撳垎缁勫ご)
                        groups.forEach { group ->
                            if (group.name.isNotBlank()) {
                                // 鍒嗙粍鏍囬
                                item {
                                    GroupHeader(
                                        groupName = group.name,
                                        iconEmoji = group.iconEmoji,
                                    )
                                }
                            }

                            // 鍒嗙粍鍐呯殑椤圭洰
                            items(group.items, key = { it.key }) { item ->
                                MultiSelectItemRow(
                                    item = item,
                                    isSelected = item.key in selectedKeys,
                                    onSelectionChange = { isSelected ->
                                        onSelectionChange(item.key, isSelected)
                                    },
                                    trailing = itemTrailing,
                                )
                            }

                            // 鍒嗙粍闂磋窛
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }

                    // 搴曢儴淇℃伅鍖猴細鎬诲ぇ灏忔樉绀猴紙鍦ㄦ搷浣滄寜閽笂鏂癸級
                    // 鏍囬鏍忔湁鍔ㄤ綔鎸夐挳鏃讹紝宸查€夋暟閲忎粠鏍囬鏍忎笅绉诲埌杩欓噷灞曠ず
                    if (totalSize != null || headerAction != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (headerAction != null) {
                                Text(
                                    text = stringResource(
                                        R.string.multi_select_selected_count,
                                        selectedItems.size,
                                        groups.sumOf { it.items.size },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (totalSize != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                            if (totalSize != null) {
                                Text(
                                    text = stringResource(R.string.multi_select_total_size, totalSize),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    // 鎿嶄綔鎸夐挳
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onSelectAll,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = 96.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.select_all),
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                            )
                        }

                        OutlinedButton(
                            onClick = onDeselectAll,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = 96.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.un_select_all),
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                            )
                        }

                        Button(
                            onClick = onConfirm ?: onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minWidth = 96.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ok),
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 鍒嗙粍鏍囬
 */
@Composable
private fun GroupHeader(
    groupName: String,
    iconEmoji: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconEmoji != null) {
            Text(
                text = iconEmoji,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
        }

        Text(
            text = groupName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        Divider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )
    }
}

/**
 * 澶氶€夐」鐩
 */
@Composable
private fun MultiSelectItemRow(
    item: MultiSelectItem,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    trailing: (@Composable (MultiSelectItem) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 澶嶉€夋
        Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectionChange,
            modifier = Modifier.padding(end = 8.dp),
        )

        // 鍐呭鍖哄煙
        Column(
            modifier = Modifier.weight(1f),
        ) {
            // 涓绘爣棰樿
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.iconEmoji != null) {
                    Text(
                        text = item.iconEmoji,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            // 鍓爣棰樺拰璇︾粏淇℃伅
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 鍓爣棰?(鏂囦欢鍚?
                if (item.subtitle != null) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                // 鏁伴噺淇℃伅
                if (item.count != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = item.count,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }

                // 澶у皬淇℃伅
                if (item.size != null) {
                    Text(
                        text = item.size,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }

        // 琛屽熬鎻掓Ы (濡傞獙璇佺姸鎬佸浘鏍?
        trailing?.invoke(item)
    }
}
