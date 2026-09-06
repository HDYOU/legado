package io.legado.app.model.analyzeRule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 变量存储并发安全回归测试。
 *
 * 背景：渐进式目录加载 / 预下载 / 缓存服务会让多条规则的 JS 同时读写同一个
 * ruleData 的 variableMap（如书源在目录规则和正文规则中同时 putVariable("custom")）。
 * 历史上 variableMap 使用普通 HashMap，并发写入 + 序列化会触发
 * ConcurrentModificationException 或静默丢数据，导致书源变量写入"偶尔失效"。
 */
class RuleDataVariableConcurrencyTest {

    private val threadCount = 8
    private val putsPerThread = 300

    @Test
    fun `concurrent putVariable distinct keys all readable`() {
        val ruleData = RuleData()
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
                        ruleData.putVariable("k${t}_$i", "v${t}_$i")
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
        repeat(threadCount) { t ->
            repeat(putsPerThread) { i ->
                assertEquals("v${t}_$i", ruleData.getVariable("k${t}_$i"))
            }
        }
    }

    @Test
    fun `concurrent getVariable during writes no exception`() {
        val ruleData = RuleData()
        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val errors = Collections.synchronizedList(mutableListOf<Exception>())

        // 写线程
        repeat(threadCount / 2) { t ->
            pool.execute {
                ready.countDown()
                start.await()
                try {
                    repeat(putsPerThread) { i ->
                        ruleData.putVariable("k${t}_$i", "v${t}_$i")
                    }
                } catch (e: Exception) {
                    errors.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        // 读线程：遍历序列化整个 map，模拟 GSON.toJson
        repeat(threadCount / 2) {
            pool.execute {
                ready.countDown()
                start.await()
                try {
                    repeat(putsPerThread) {
                        ruleData.getVariable()
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
        assertTrue("并发读写超时", done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        assertTrue("并发读写抛出异常: ${errors.take(3)}", errors.isEmpty())
    }

    @Test
    fun `putVariable same key from many threads keeps valid json`() {
        val ruleData = RuleData()
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
                        ruleData.putVariable("custom", "$t-$i")
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
        assertTrue("并发写同键超时", done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        assertTrue("写入抛出异常: ${errors.take(3)}", errors.isEmpty())
        // json 反序列化后必须与内存 map 一致且值合法
        val json = ruleData.getVariable()
        assertTrue(json != null && json.contains("custom"))
        assertTrue(ruleData.getVariable("custom").isNotBlank())
    }
}
