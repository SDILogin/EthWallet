package me.sdi.ethwallet.core.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class GasParamsTest {
    @Test
    fun `Default is a singleton object`() {
        val default1 = GasParams.Default
        val default2 = GasParams.Default
        assertTrue(default1 === default2)
    }

    @Test
    fun `Default implements GasParams interface`() {
        val params: GasParams = GasParams.Default
        assertTrue(params is GasParams.Default)
    }

    @Test
    fun `Custom stores gas price correctly`() {
        val gasPrice = BigInteger("20000000000") // 20 gwei
        val gasLimit = BigInteger("21000")
        val custom = GasParams.Custom(gasPrice, gasLimit)
        assertEquals(gasPrice, custom.gasPrice)
    }

    @Test
    fun `Custom stores gas limit correctly`() {
        val gasPrice = BigInteger("20000000000")
        val gasLimit = BigInteger("21000")
        val custom = GasParams.Custom(gasPrice, gasLimit)
        assertEquals(gasLimit, custom.gasLimit)
    }

    @Test
    fun `Custom implements GasParams interface`() {
        val params: GasParams = GasParams.Custom(BigInteger.ONE, BigInteger.ONE)
        assertTrue(params is GasParams.Custom)
    }

    @Test
    fun `Custom with same values should be equal`() {
        val custom1 = GasParams.Custom(BigInteger("20000000000"), BigInteger("21000"))
        val custom2 = GasParams.Custom(BigInteger("20000000000"), BigInteger("21000"))
        assertEquals(custom1, custom2)
    }

    @Test
    fun `Custom with different gas price should not be equal`() {
        val custom1 = GasParams.Custom(BigInteger("20000000000"), BigInteger("21000"))
        val custom2 = GasParams.Custom(BigInteger("30000000000"), BigInteger("21000"))
        assertNotEquals(custom1, custom2)
    }

    @Test
    fun `Custom with different gas limit should not be equal`() {
        val custom1 = GasParams.Custom(BigInteger("20000000000"), BigInteger("21000"))
        val custom2 = GasParams.Custom(BigInteger("20000000000"), BigInteger("50000"))
        assertNotEquals(custom1, custom2)
    }

    @Test
    fun `Custom allows zero gas price`() {
        val custom = GasParams.Custom(BigInteger.ZERO, BigInteger("21000"))
        assertEquals(BigInteger.ZERO, custom.gasPrice)
    }

    @Test
    fun `Custom allows zero gas limit`() {
        val custom = GasParams.Custom(BigInteger("20000000000"), BigInteger.ZERO)
        assertEquals(BigInteger.ZERO, custom.gasLimit)
    }

    @Test
    fun `Custom allows large gas values`() {
        val largeGasPrice = BigInteger("1000000000000000000") // 1 ETH in wei
        val largeGasLimit = BigInteger("10000000")
        val custom = GasParams.Custom(largeGasPrice, largeGasLimit)
        assertEquals(largeGasPrice, custom.gasPrice)
        assertEquals(largeGasLimit, custom.gasLimit)
    }
}
