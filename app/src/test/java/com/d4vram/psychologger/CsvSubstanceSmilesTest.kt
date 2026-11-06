package com.d4vram.psychologger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Method

/**
 * Comprueba que el parser CSV de sustancias lee la novena columna opcional (SMILES)
 * y que los CSV antiguos (sin ella) siguen entrando igual.
 * WebAppInterface necesita Context/Activity: se instancia sin constructor y se llama por reflexión.
 */
class CsvSubstanceSmilesTest {
    private val target: Any by lazy {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null)
        unsafe.javaClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, WebAppInterface::class.java)
    }

    private fun call(name: String, line: String): Any? {
        val parse: Method = WebAppInterface::class.java.declaredMethods.first { it.name == "parseCsvLine" }.apply { isAccessible = true }
        val sub: Method = WebAppInterface::class.java.declaredMethods.first { it.name == name }.apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val cols = parse.invoke(target, line) as List<String>
        return sub.invoke(target, cols)
    }

    private fun smilesOf(result: Any?): String? = result?.javaClass?.getDeclaredMethod("getSmiles")?.invoke(result) as String?
    private fun nameOf(result: Any?): String? = result?.javaClass?.getDeclaredMethod("getName")?.invoke(result) as String?

    @Test fun newFormatSemicolon_readsSmiles() {
        val r = call("parseSubstance", "abc;\"Mi molecula\";\"#8B5CF6\";\"🧬\";false;\"\";\"2026-01-01\";\"\";\"CC(=O)Oc1ccccc1C(=O)O\"")
        assertEquals("Mi molecula", nameOf(r)); assertEquals("CC(=O)Oc1ccccc1C(=O)O", smilesOf(r))
    }

    @Test fun newFormatComma_readsSmiles() {
        val r = call("parseSubstance", "abc,\"LSD\",\"#8B5CF6\",\"🌈\",false,\"\",\"2026-01-01\",\"\",\"CCN(CC)C(=O)[C@H]1CN(C)C\\C1\"")
        assertEquals("LSD", nameOf(r)); assertEquals("CCN(CC)C(=O)[C@H]1CN(C)C\\C1", smilesOf(r))
    }

    @Test fun emptySmilesColumn_isNull() {
        val r = call("parseSubstance", "abc;\"LSD\";\"#8B5CF6\";\"🌈\";false;\"\";\"2026-01-01\";\"\";\"\"")
        assertEquals("LSD", nameOf(r)); assertNull(smilesOf(r))
    }

    @Test fun oldFormatEightColumns_stillImports() {
        val r = call("parseSubstance", "abc;\"LSD\";\"#8B5CF6\";\"🌈\";false;\"\";\"2026-01-01\";\"\"")
        assertEquals("LSD", nameOf(r)); assertNull(smilesOf(r))
    }

    @Test fun oldFormatSixColumns_stillImports() {
        val r = call("parseSubstance", "abc;\"LSD\";\"#8B5CF6\";\"🌈\";\"2026-01-01\";\"\"")
        assertEquals("LSD", nameOf(r)); assertNull(smilesOf(r))
    }
}
