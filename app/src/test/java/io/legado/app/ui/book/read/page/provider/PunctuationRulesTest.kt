package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标点禁则字符分类的单元测试。
 *
 * [PunctuationRules] 是「标点允许出现在行首」功能的语义边界：`ZhLayout` 在常规模式下
 * 依据它决定是否把字符下移，在 `ignorePunctRules` 模式下则整体跳过这些判定。
 * 分类错一个字符，排版就会在该避头/避尾的位置漏判。
 */
class PunctuationRulesTest {

    @Test
    fun `收尾标点覆盖中英文常见形态`() {
        listOf(
            "，", "。", "：", "？", "！", "、",      // 中文句读
            "”", "’", "）", "》", "】", "」", "；",   // 中文收尾引号/括号/分号
            ",", ".", "?", "!", ":", ";", ")", "]", "}", ">" // 半角
        ).forEach { punct ->
            assertTrue("「$punct」应被识别为收尾标点", PunctuationRules.isPostPunctuation(punct))
        }
    }

    @Test
    fun `起首标点覆盖中英文常见形态`() {
        listOf(
            "“", "‘", "（", "《", "【", "「",  // 中文起首
            "(", "<", "[", "{",                // 半角
        ).forEach { punct ->
            assertTrue("「$punct」应被识别为起首标点", PunctuationRules.isPrePunctuation(punct))
        }
    }

    @Test
    fun `普通汉字与字母不参与禁则判定`() {
        listOf("春", "风", "a", "Z", "1", " ").forEach { ch ->
            assertFalse("「$ch」不应是收尾标点", PunctuationRules.isPostPunctuation(ch))
            assertFalse("「$ch」不应是起首标点", PunctuationRules.isPrePunctuation(ch))
        }
    }

    @Test
    fun `收尾与起首集合互不重叠`() {
        val overlap = PunctuationRules.POST_PUNCTUATION intersect PunctuationRules.PRE_PUNCTUATION
        assertTrue("两个集合不应有交集，实际为：$overlap", overlap.isEmpty())
    }

    @Test
    fun `禁则判定对多字符输入不误判`() {
        // ZhLayout 传入的是单个字符簇（见 measureTextSplit），但语义上是"集合命中"，
        // 多字符串不应命中，否则会把整段误判成标点。
        assertFalse(PunctuationRules.isPostPunctuation("，，"))
        assertFalse(PunctuationRules.isPrePunctuation("““"))
        assertFalse(PunctuationRules.isPostPunctuation("春。"))
        assertFalse(PunctuationRules.isPrePunctuation("春“"))
        assertFalse(PunctuationRules.isPostPunctuation(""))
    }
}
