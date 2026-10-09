package io.legado.app.ui.book.read.page.provider

/**
 * 中文标点禁则的字符分类。
 *
 * 抽出来是为了让它成为**可单元测试的纯逻辑**：[ZhLayout] 继承 android.text.Layout，
 * 无法在 JVM 单测里实例化，而"哪些字符算禁首/禁尾标点"正是排版的业务语义所在。
 *
 * - [POST_PUNCTUATION]：收尾标点。常规排版下不得出现在行首（避头），因此行满时若下一
 *   字符属于此集合，要把前一个字符一起移到下一行。
 * - [PRE_PUNCTUATION]：起首标点。常规排版下不得出现在行尾（避尾），因此行满时若前一个
 *   字符属于此集合，要把该标点连同当前字符下移。
 *
 * 当 `ZhLayout` 以「无视标点规则」模式构造时（见 `ignorePunctRules`），这些判定整体跳过，
 * 收尾标点即可出现在行首。
 */
object PunctuationRules {

    /** 收尾标点（不得出现在行首） */
    val POST_PUNCTUATION: Set<String> = hashSetOf(
        "，", "。", "：", "？", "！", "、", "”", "’", "）", "》",
        "】", ")", ">", "]", "}", ",", ".", "?", "!", ":", "」", "；", ";"
    )

    /** 起首标点（不得出现在行尾） */
    val PRE_PUNCTUATION: Set<String> = hashSetOf(
        "“", "（", "《", "【", "‘", "(", "<", "[", "{", "「"
    )

    /** 是否为收尾标点（行首禁则） */
    fun isPostPunctuation(string: String): Boolean = POST_PUNCTUATION.contains(string)

    /** 是否为起首标点（行尾禁则） */
    fun isPrePunctuation(string: String): Boolean = PRE_PUNCTUATION.contains(string)
}
