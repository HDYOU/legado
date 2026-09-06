package io.legado.app.model.analyzeRule

import io.legado.app.data.entities.BaseBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * BaseBook 变量写入协议的并发回归测试。
 *
 * 覆盖 BaseBook.putVariable 的完整协议：synchronized(variableMap) 内完成
 * 「variableMap 变更 + variable JSON 序列化」，并发写入后 JSON 必须与内存
 * map 一致，读侧永远不能看到撕裂/丢 key 的 JSON。这是书源 JS 通过
 * book.putVariable / book.putCustomVariable 写变量的实际执行路径。
 */
class BaseBookVariableConcurrencyTest {

    private class FakeBook(variable: String? = null) : BaseBook {
        override var name: String = ""
        override var author: String = ""
        override var bookUrl: String = ""
        override var kind: String? = null
        override var wordCount: String? = null
        override var infoHtml: String? = null
        override var tocHtml: String? = null
        override var variable: String? = variable

        override val variableMap: ConcurrentHashMap<String, String> by lazy {
            ConcurrentHashMap(GSON.fromJsonObject<Map<String, String>>(variable).getOrNull() ?: emptyMap())
        }

        private val bigVariables = ConcurrentHashMap<String, String>()

        override fun putBigVariable(key: String, value: String?) {
            if (value == null) {
                bigVariables.remove(key)
            } else {
                bigVariables[key] = value
            }
        }

        override fun getBigVariable(key: String): String? = bigVariables[key]
    }

    private val threadCount = 8
    private val putsPerThread = 300

    @Test
    fun `concurrent putVariable keeps variable json consistent with map`() {
        val book = FakeBook()
        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val errors = Collections.synchronizedList(mutableListOf<Exception>())

        repeat(threadCount) { t ->
            pool.execute {
                ready.countDown()
                start.await()
                try {
                    repeat(putsPerThread) { i ->
                        book.putVariable("k${t}_$i", "v${t}_$i")
                    }
                } catch (e: Exception) {
                    errors.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        ready.await()
        start.countDown()
        assertTrue("并发写入超时", done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        assertTrue("写入抛出异常: ${errors.take(3)}", errors.isEmpty())
        // variable JSON 必须包含全部 key，且与内存 map 一致
        val jsonMap = GSON.fromJsonObject<Map<String, String>>(book.variable).getOrNull()!!
        assertEquals(threadCount * putsPerThread, jsonMap.size)
        assertEquals(book.variableMap, jsonMap)
    }

    @Test
    fun `concurrent putCustomVariable same key keeps valid json`() {
        val book = FakeBook()
        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val errors = Collections.synchronizedList(mutableListOf<Exception>())

        repeat(threadCount) { t ->
            pool.execute {
                ready.countDown()
                start.await()
                try {
                    repeat(putsPerThread) { i ->
                        book.putCustomVariable("第${t}页的楼层数量：$i")
                    }
                } catch (e: Exception) {
                    errors.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        ready.await()
        start.countDown()
        assertTrue("并发写入超时", done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        assertTrue("写入抛出异常: ${errors.take(3)}", errors.isEmpty())
        assertTrue("variable JSON 不应缺失 custom", book.variable!!.contains("custom"))
        assertTrue(book.getCustomVariable().isNotBlank())
    }

    @Test
    fun `big variable write during small writes stays readable`() {
        val book = FakeBook()
        val bigValue = "x".repeat(20000)
        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val errors = Collections.synchronizedList(mutableListOf<Exception>())

        pool.execute {
            ready.countDown()
            start.await()
            try {
                repeat(putsPerThread) { i ->
                    book.putVariable("big$i", bigValue)
                    book.putVariable("small$i", "s$i")
                }
            } catch (e: Exception) {
                errors.add(e)
            } finally {
                done.countDown()
            }
        }
        repeat(threadCount - 1) { t ->
            pool.execute {
                ready.countDown()
                start.await()
                try {
                    repeat(putsPerThread) { i ->
                        book.putVariable("k${t}_$i", "v${t}_$i")
                    }
                } catch (e: Exception) {
                    errors.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        ready.await()
        start.countDown()
        assertTrue("并发写入超时", done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        assertTrue("写入抛出异常: ${errors.take(3)}", errors.isEmpty())
        // 大变量走 RuleBigData 存储，getVariable 需能读回；小变量在 JSON 中
        assertEquals(bigValue, book.getVariable("big0"))
        assertEquals("s0", book.getVariable("small0"))
        val jsonMap = GSON.fromJsonObject<Map<String, String>>(book.variable).getOrNull()!!
        assertTrue(jsonMap.containsKey("small0"))
        assertEquals(book.variableMap, jsonMap)
    }
}
