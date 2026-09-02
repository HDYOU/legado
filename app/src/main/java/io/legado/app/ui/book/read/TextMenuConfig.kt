package io.legado.app.ui.book.read

import android.content.Context
import android.util.Log
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref

/**
 * 文本菜单项配置管理
 *
 * 功能说明：
 * 管理文本操作菜单项的显示/隐藏、排序与自定义标题
 *
 * 持久化约定（重要）：
 * 所有配置均以菜单项的【资源 entry name】（如 "menu_replace"）作为稳定标识存储，
 * 绝不能直接存储 R.id 数值 —— AAPT2 每次构建都会重新分配 ID 数值，
 * 覆盖安装新版本后旧 ID 将无法匹配，导致配置"重置为默认值"。
 * 旧版本以纯数字 ID 存储的配置在读取时无法映射，会被忽略并回退默认（与修复前行为一致）。
 *
 * 使用方法：
 * 1. 获取所有菜单项：TextMenuConfig.getAllMenuItems()
 * 2. 获取隐藏的菜单项ID：TextMenuConfig.getHiddenMenuItemIds(context)
 * 3. 设置隐藏的菜单项：TextMenuConfig.setHiddenMenuItemIds(context, ids)
 * 4. 切换菜单项显示状态：TextMenuConfig.toggleMenuItem(context, itemId)
 */
object TextMenuConfig {

    private const val TAG = "TextMenuConfig"
    const val DEFAULT_VISIBLE_COUNT = 7
    const val MIN_VISIBLE_COUNT = 3
    const val MAX_VISIBLE_COUNT = 10

    /**
     * 菜单项信息
     * @param name 稳定标识（资源 entry name），跨版本持久化使用
     * @param id 菜单项ID（当前构建的 R.id 数值，仅运行期使用）
     * @param nameResId 菜单项名称资源ID
     * @param defaultVisible 默认是否可见
     */
    data class MenuItemInfo(
        val name: String,
        val id: Int,
        val nameResId: Int,
        val defaultVisible: Boolean = true
    )

    /**
     * 所有可配置的菜单项
     * 注意：这个列表的顺序决定了菜单项的显示顺序
     */
    val ALL_MENU_ITEMS = listOf(
        MenuItemInfo("menu_replace", R.id.menu_replace, R.string.replace),
        MenuItemInfo("menu_copy", R.id.menu_copy, android.R.string.copy),
        MenuItemInfo("menu_bookmark", R.id.menu_bookmark, R.string.bookmark),
        MenuItemInfo("menu_aloud", R.id.menu_aloud, R.string.read_aloud),
        MenuItemInfo("menu_dict", R.id.menu_dict, R.string.dict),
        MenuItemInfo("menu_share_image", R.id.menu_share_image, R.string.share_note_menu),
        MenuItemInfo("menu_web_search", R.id.menu_web_search, R.string.web_search),
        MenuItemInfo("menu_text_menu_config", R.id.menu_text_menu_config, R.string.menu_config),
        MenuItemInfo("menu_highlight_rule", R.id.menu_highlight_rule, R.string.menu_highlight_rule),
        MenuItemInfo("menu_search_content", R.id.menu_search_content, R.string.search_content),
        MenuItemInfo("menu_browser", R.id.menu_browser, R.string.browser),
        MenuItemInfo("menu_share_str", R.id.menu_share_str, R.string.share)
    )

    /** name -> id 映射，用于把持久化的稳定标识还原为当前构建的 ID */
    private val idByName: Map<String, Int> by lazy {
        ALL_MENU_ITEMS.associate { it.name to it.id }
    }

    /** id -> name 映射，用于把运行期 ID 转换为可持久化的稳定标识 */
    private val nameById: Map<Int, String> by lazy {
        ALL_MENU_ITEMS.associate { it.id to it.name }
    }

    private fun nameOf(context: Context, id: Int): String? {
        nameById[id]?.let { return it }
        // 兜底：处理不在 ALL_MENU_ITEMS 中的菜单项
        return runCatching { context.resources.getResourceEntryName(id) }.getOrNull()
    }

    private fun idOf(context: Context, name: String): Int? {
        idByName[name]?.let { return it }
        // 兜底：通过资源名反查（找不到时返回 0，视为未知项丢弃）
        val id = context.resources.getIdentifier(name, "id", context.packageName)
        return if (id != 0) id else null
    }

    /**
     * 获取所有菜单项列表
     * 如果用户自定义了排序，返回排序后的列表；否则返回默认顺序
     */
    fun getAllMenuItems(context: Context): List<MenuItemInfo> {
        val orderNames = getOrderNames(context)
        if (orderNames.isEmpty()) return ALL_MENU_ITEMS
        val itemMap = ALL_MENU_ITEMS.associateBy { it.name }
        // 按存储的顺序排列已知项，未知的新项追加到末尾
        val ordered = orderNames.mapNotNull { name -> itemMap[name] }
        val remaining = ALL_MENU_ITEMS.filter { it.name !in orderNames }
        return ordered + remaining
    }

    /**
     * 获取菜单项排序（ID列表）
     */
    fun getMenuItemOrder(context: Context): List<Int> {
        return getOrderNames(context).mapNotNull { idOf(context, it) }
    }

    /**
     * 读取排序存储，返回稳定标识（资源名）列表。
     * 旧版本以 JSON 数字数组存储，GSON 宽松解析为数字字符串后无法匹配任何资源名，自然丢弃。
     */
    private fun getOrderNames(context: Context): List<String> {
        val json = context.getPrefString(PreferKey.textMenuItemOrder)
        if (json.isNullOrEmpty()) return emptyList()
        return GSON.fromJsonArray<String>(json).getOrNull() ?: emptyList()
    }

    /**
     * 设置菜单项排序
     * @param order 菜单项ID列表，按显示顺序排列
     */
    fun setMenuItemOrder(context: Context, order: List<Int>) {
        val names = order.mapNotNull { nameOf(context, it) }
        context.putPrefString(PreferKey.textMenuItemOrder, GSON.toJson(names))
    }

    fun getDefaultMenuTitle(context: Context, item: MenuItemInfo): String {
        return context.getString(item.nameResId)
    }

    fun getCustomMenuTitles(context: Context): Map<Int, String> {
        val json = context.getPrefString(PreferKey.textMenuCustomTitles)
        return GSON.fromJsonObject<Map<String, String>>(json).getOrNull()
            ?.mapNotNull { (name, value) ->
                idOf(context, name)?.let { id -> id to value }
            }
            ?.toMap()
            ?: emptyMap()
    }

    fun getCustomMenuTitle(context: Context, itemId: Int): String? {
        return getCustomMenuTitles(context)[itemId]?.takeIf { it.isNotBlank() }
    }

    fun getMenuTitle(context: Context, item: MenuItemInfo): String {
        return getCustomMenuTitle(context, item.id) ?: getDefaultMenuTitle(context, item)
    }

    fun setCustomMenuTitle(context: Context, itemId: Int, title: String?) {
        val name = nameOf(context, itemId) ?: return
        val titles = GSON.fromJsonObject<Map<String, String>>(
            context.getPrefString(PreferKey.textMenuCustomTitles)
        ).getOrNull()?.toMutableMap() ?: mutableMapOf()
        val normalizedTitle = title?.trim().orEmpty()
        if (normalizedTitle.isBlank()) {
            titles.remove(name)
        } else {
            titles[name] = normalizedTitle
        }
        context.putPrefString(PreferKey.textMenuCustomTitles, GSON.toJson(titles))
    }

    fun getTextMenuVisibleCount(context: Context): Int {
        return context.getPrefInt(
            PreferKey.textMenuVisibleCount,
            DEFAULT_VISIBLE_COUNT
        ).coerceIn(MIN_VISIBLE_COUNT, MAX_VISIBLE_COUNT)
    }

    fun setTextMenuVisibleCount(context: Context, count: Int) {
        context.putPrefInt(
            PreferKey.textMenuVisibleCount,
            count.coerceIn(MIN_VISIBLE_COUNT, MAX_VISIBLE_COUNT)
        )
    }

    /**
     * 获取隐藏的菜单项ID集合
     */
    fun getHiddenMenuItemIds(context: Context): Set<Int> {
        val hiddenStr = context.getPrefString(PreferKey.hiddenTextMenuItems, "")
        Log.d(TAG, "getHiddenMenuItemIds: hiddenStr='$hiddenStr'")
        if (hiddenStr.isNullOrEmpty()) return emptySet()
        return hiddenStr.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { idOf(context, it) }
            .toSet()
            .also { Log.d(TAG, "getHiddenMenuItemIds: ids=$it") }
    }

    /**
     * 设置隐藏的菜单项ID集合
     */
    fun setHiddenMenuItemIds(context: Context, ids: Set<Int>) {
        val hiddenStr = ids.mapNotNull { nameOf(context, it) }.joinToString(",")
        Log.d(TAG, "setHiddenMenuItemIds: ids=$ids, hiddenStr='$hiddenStr'")
        context.putPrefString(PreferKey.hiddenTextMenuItems, hiddenStr)
    }

    /**
     * 切换菜单项的显示/隐藏状态
     * @return 切换后的状态（true=显示，false=隐藏）
     */
    fun toggleMenuItem(context: Context, itemId: Int): Boolean {
        val hiddenIds = getHiddenMenuItemIds(context).toMutableSet()
        val isCurrentlyHidden = itemId in hiddenIds

        Log.d(TAG, "toggleMenuItem: itemId=$itemId, isCurrentlyHidden=$isCurrentlyHidden")

        if (isCurrentlyHidden) {
            // 当前是隐藏状态，切换为显示
            hiddenIds.remove(itemId)
        } else {
            // 当前是显示状态，切换为隐藏
            hiddenIds.add(itemId)
        }

        setHiddenMenuItemIds(context, hiddenIds)
        val newState = !isCurrentlyHidden
        Log.d(TAG, "toggleMenuItem: newState=$newState")
        return newState  // 返回切换后的状态
    }

    /**
     * 检查菜单项是否被隐藏
     */
    fun isMenuItemHidden(context: Context, itemId: Int): Boolean {
        return itemId in getHiddenMenuItemIds(context)
    }

    /**
     * 重置为默认配置（所有菜单项都显示）
     */
    fun resetToDefault(context: Context) {
        Log.d(TAG, "resetToDefault")
        context.putPrefString(PreferKey.hiddenTextMenuItems, "")
        context.putPrefString(PreferKey.textMenuCustomTitles, "")
        context.removePref(PreferKey.textMenuItemOrder)
        setTextMenuVisibleCount(context, DEFAULT_VISIBLE_COUNT)
    }

    // ==================== 其他应用菜单配置 ====================

    /**
     * 生成其他应用菜单项的唯一标识
     * 格式：包名/类名（本身即跨版本稳定）
     */
    fun getProcessTextItemKey(packageName: String, className: String): String {
        return "$packageName/$className"
    }

    fun getCustomProcessTextTitles(context: Context): Map<String, String> {
        val json = context.getPrefString(PreferKey.processTextCustomTitles)
        return GSON.fromJsonObject<Map<String, String>>(json).getOrNull() ?: emptyMap()
    }

    fun getCustomProcessTextTitle(context: Context, key: String): String? {
        return getCustomProcessTextTitles(context)[key]?.takeIf { it.isNotBlank() }
    }

    fun setCustomProcessTextTitle(context: Context, key: String, title: String?) {
        val titles = getCustomProcessTextTitles(context).toMutableMap()
        val normalizedTitle = title?.trim().orEmpty()
        if (normalizedTitle.isBlank()) {
            titles.remove(key)
        } else {
            titles[key] = normalizedTitle
        }
        context.putPrefString(PreferKey.processTextCustomTitles, GSON.toJson(titles))
    }

    /**
     * 获取隐藏的其他应用菜单项集合
     */
    fun getHiddenProcessTextItems(context: Context): Set<String> {
        val hiddenStr = context.getPrefString(PreferKey.hiddenProcessTextItems, "")
        Log.d(TAG, "getHiddenProcessTextItems: hiddenStr='$hiddenStr'")
        return if (hiddenStr.isNullOrEmpty()) {
            emptySet()
        } else {
            hiddenStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    /**
     * 设置隐藏的其他应用菜单项集合
     */
    fun setHiddenProcessTextItems(context: Context, keys: Set<String>) {
        val hiddenStr = keys.joinToString(",")
        Log.d(TAG, "setHiddenProcessTextItems: keys=$keys, hiddenStr='$hiddenStr'")
        context.putPrefString(PreferKey.hiddenProcessTextItems, hiddenStr)
    }

    /**
     * 检查其他应用菜单项是否被隐藏
     */
    fun isProcessTextItemHidden(context: Context, packageName: String, className: String): Boolean {
        val key = getProcessTextItemKey(packageName, className)
        return key in getHiddenProcessTextItems(context)
    }

    /**
     * 重置其他应用菜单配置（全部显示）
     */
    fun resetProcessTextConfig(context: Context) {
        Log.d(TAG, "resetProcessTextConfig")
        context.putPrefString(PreferKey.hiddenProcessTextItems, "")
        context.putPrefString(PreferKey.processTextCustomTitles, "")
    }
}
