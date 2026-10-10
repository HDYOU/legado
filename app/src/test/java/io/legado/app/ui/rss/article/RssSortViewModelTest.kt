package io.legado.app.ui.rss.article

import android.content.Intent
import io.legado.app.MainDispatcherRule
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.data.entities.RssSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * RSS 分类/排序 ViewModel 状态测试（testing.md §16：runTest + 手写 Fake）
 * 覆盖：initData 源加载/回退、布局样式循环、文章清空与序号、记录统计与删除。
 * clearSortCache 依赖文件缓存，不在本测试范围。
 */
class RssSortViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private fun source(url: String, style: Int = 0, name: String = "") = RssSource(sourceUrl = url, sourceName = name, articleStyle = style)

    private fun record(record: String, origin: String) = RssReadRecord(record = record, origin = origin)

    /** 注入与 Main 共享时钟的测试调度器，保证 advanceUntilIdle 收敛 body 队列 */
    private fun newViewModel(repository: FakeRssSortRepository) = RssSortViewModel(repository, StandardTestDispatcher(mainDispatcherRule.dispatcher.scheduler))

    @Test
    fun `initData 加载已存在源并设置各字段`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRssSortRepository().apply {
            sources["url1"] = source("url1", style = 2, name = "源A")
        }
        val viewModel = newViewModel(repository)
        val intent = mockk<Intent>().apply {
            every { getStringExtra("sourceUrl") } returns "url1"
            every { getStringExtra("sortUrl") } returns "sort-2"
            every { getStringExtra("key") } returns "关键字"
        }
        var done = false

        viewModel.initData(intent) { done = true }
        advanceUntilIdle()

        assertEquals("url1", viewModel.url)
        assertEquals("源A", viewModel.sourceName)
        assertEquals("源A", viewModel.rssSource?.sourceName)
        assertEquals("sort-2", viewModel.sortUrl)
        assertEquals("关键字", viewModel.searchKey)
        assertTrue("onFinally 未回调", done)
    }

    @Test
    fun `initData 未携带 sortUrl 时保留已有分类`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRssSortRepository().apply {
            sources["url1"] = source("url1", name = "源A")
        }
        val viewModel = newViewModel(repository)
        // 模拟已进入指定分类页（如 java.open("sort") 打开的热搜）
        viewModel.sortUrl = "sort-specified"
        val intent = mockk<Intent>().apply {
            every { getStringExtra("sourceUrl") } returns "url1"
            every { getStringExtra("sortUrl") } returns null
            every { getStringExtra("key") } returns "关键字"
        }
        var done = false

        viewModel.initData(intent) { done = true }
        advanceUntilIdle()

        assertEquals("sort-specified", viewModel.sortUrl)
        assertEquals("关键字", viewModel.searchKey)
        assertTrue("onFinally 未回调", done)
    }

    @Test
    fun `initData 未知源回退为新建源`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRssSortRepository()
        val viewModel = newViewModel(repository)
        val intent = mockk<Intent>().apply {
            every { getStringExtra("sourceUrl") } returns "no-such"
            every { getStringExtra("sortUrl") } returns null
            every { getStringExtra("key") } returns null
        }
        var done = false

        viewModel.initData(intent) { done = true }
        advanceUntilIdle()

        assertEquals("no-such", viewModel.url)
        assertNotNull(viewModel.rssSource)
        assertEquals("no-such", viewModel.rssSource?.sourceUrl)
        assertNull(viewModel.sourceName)
        assertTrue("onFinally 未回调", done)
    }

    @Test
    fun `switchLayout 样式递进到 4 后回绕为 0`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRssSortRepository()
        val viewModel = newViewModel(repository)
        viewModel.rssSource = source("url1", style = 3)

        viewModel.switchLayout()
        advanceUntilIdle()

        assertEquals(4, viewModel.rssSource?.articleStyle)

        viewModel.rssSource = source("url2", style = 4)
        viewModel.switchLayout()
        advanceUntilIdle()

        assertEquals(0, viewModel.rssSource?.articleStyle)
        assertEquals(listOf(4, 0), repository.savedStyles)
    }

    @Test
    fun `switchLayout 未加载源时不更新数据源`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRssSortRepository()
        val viewModel = newViewModel(repository)

        viewModel.switchLayout()
        advanceUntilIdle()

        assertTrue(repository.savedStyles.isEmpty())
    }

    @Test
    fun `clearArticles 清空该源文章并重置序号`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRssSortRepository()
        val viewModel = newViewModel(repository)
        viewModel.url = "url1"
        val oldOrder = viewModel.order

        viewModel.clearArticles()
        advanceUntilIdle()

        assertEquals(listOf("url1"), repository.deletedArticles)
        assertTrue("序号应前移", viewModel.order >= oldOrder)
    }

    @Test
    fun `getRecords 与 countRecords 支持按源过滤`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRssSortRepository().apply {
            records += record("r1", "o1")
            records += record("r2", "o1")
            records += record("r3", "o2")
        }
        val viewModel = newViewModel(repository)

        assertEquals(2, viewModel.countRecords("o1"))
        assertEquals(3, viewModel.countRecords())
        assertEquals(listOf("r1", "r2"), viewModel.getRecords("o1").map { it.record })
    }

    @Test
    fun `deleteAllRecord 支持全部与按源删除`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRssSortRepository()
        val viewModel = newViewModel(repository)

        viewModel.deleteAllRecord()
        viewModel.deleteAllRecord("o1")
        advanceUntilIdle()

        assertEquals(listOf(null, "o1"), repository.deletedOrigins)
    }

    @Test
    fun `clearArticles 无 url 时只重置序号`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeRssSortRepository()
        val viewModel = newViewModel(repository)
        val oldOrder = viewModel.order

        viewModel.clearArticles()
        advanceUntilIdle()

        assertTrue(repository.deletedArticles.isEmpty())
        assertTrue(viewModel.order >= oldOrder)
    }
}

/**
 * 手写 Fake：只实现被测路径用到的方法，行为为真实数据迁移（照 FakeReadRecordDao 模式）
 */
private class FakeRssSortRepository : RssSortRepository {

    val sources = mutableMapOf<String, RssSource>()
    val savedStyles = mutableListOf<Int>()
    val deletedArticles = mutableListOf<String>()
    val deletedOrigins = mutableListOf<String?>()
    val records = mutableListOf<RssReadRecord>()

    override fun getSourceByKey(key: String): RssSource? = sources[key]

    override fun updateSource(source: RssSource) {
        savedStyles += source.articleStyle
    }

    override fun deleteArticles(sourceUrl: String) {
        deletedArticles += sourceUrl
    }

    override fun getRecords(origin: String?): List<RssReadRecord> = if (origin == null) records else records.filter { it.origin == origin }

    override fun countRecords(origin: String?): Int = getRecords(origin).size

    override fun deleteRecords(origin: String?) {
        deletedOrigins += origin
    }
}
