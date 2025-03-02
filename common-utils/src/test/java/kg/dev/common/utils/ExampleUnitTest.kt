package kg.dev.common.utils

import com.google.gson.internal.LinkedTreeMap
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    private fun linkedTreeMapOf(vararg pairs: Pair<String, Any>): LinkedTreeMap<String, Any> {
        return LinkedTreeMap<String, Any>().apply { putAll(pairs) }
    }

    @Test
    fun `should find key in first LinkedTreeMap in list`() {
        val list = arrayListOf(
            linkedTreeMapOf("key1" to "value1"),
            linkedTreeMapOf("key2" to "value2")
        )

        assertEquals("value1", list.extractDataPack<String>("key1"))
    }

    @Test
    fun `should find key in last LinkedTreeMap in list`() {
        val list = arrayListOf(
            linkedTreeMapOf("key1" to "value1"),
            linkedTreeMapOf("key2" to "value2")
        )

        assertEquals("value2", list.extractDataPack<String>("key2"))
    }

    @Test
    fun `should find key in deeply nested LinkedTreeMap in list`() {
        val list = arrayListOf(
            linkedTreeMapOf(
                "nested" to linkedTreeMapOf(
                    "deepKey" to "deepValue"
                )
            )
        )

        assertEquals("deepValue", list.extractDataPack<String>("deepKey"))
    }

    @Test
    fun `should find key in nested list of LinkedTreeMaps`() {
        val list = arrayListOf(
            linkedTreeMapOf(
                "list" to listOf(
                    linkedTreeMapOf("nestedKey" to "nestedValue")
                )
            )
        )

        assertEquals("nestedValue", list.extractDataPack<String>("nestedKey"))
    }

    @Test
    fun `should return null for non-existing key in list of LinkedTreeMaps`() {
        val list = arrayListOf(
            linkedTreeMapOf("key1" to "value1")
        )

        assertNull(list.extractDataPack<String>("nonExistentKey"))
    }

    @Test
    fun `should return null for empty list of LinkedTreeMaps`() {
        val emptyList = arrayListOf<LinkedTreeMap<String, Any>>()
        assertNull(emptyList.extractDataPack<String>("anyKey"))
    }
}