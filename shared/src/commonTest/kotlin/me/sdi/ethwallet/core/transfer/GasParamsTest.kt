package me.sdi.ethwallet.core.transfer

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GasParamsTest {
    @Test
    fun Default_is_a_singleton_object() {
        val default1 = GasParams.Default
        val default2 = GasParams.Default
        assertTrue(default1 === default2)
    }

    @Test
    fun Default_implements_GasParams_interface() {
        val params: GasParams = GasParams.Default
        assertTrue(params is GasParams.Default)
    }

    @Test
    fun Custom_stores_gas_price_correctly() {
        val gasPrice = BigInteger("20000000000") // 20 gwei
        val gasLimit = BigInteger("21000")
        val custom = GasParams.Custom(gasPrice, gasLimit)
        assertEquals(gasPrice, custom.gasPrice)
    }

    @Test
    fun Custom_stores_gas_limit_correctly() {
        val gasPrice = BigInteger("20000000000")
        val gasLimit = BigInteger("21000")
        val custom = GasParams.Custom(gasPrice, gasLimit)
        assertEquals(gasLimit, custom.gasLimit)
    }

    @Test
    fun Custom_implements_GasParams_interface() {
        val params: GasParams = GasParams.Custom(BigInteger.ONE, BigInteger.ONE)
        assertTrue(params is GasParams.Custom)
    }

    @Test
    fun Custom_with_same_values_should_be_equal() {
        val custom1 = GasParams.Custom(BigInteger("20000000000"), BigInteger("21000"))
        val custom2 = GasParams.Custom(BigInteger("20000000000"), BigInteger("21000"))
        assertEquals(custom1, custom2)
    }

    @Test
    fun Custom_with_different_gas_price_should_not_be_equal() {
        val custom1 = GasParams.Custom(BigInteger("20000000000"), BigInteger("21000"))
        val custom2 = GasParams.Custom(BigInteger("30000000000"), BigInteger("21000"))
        assertNotEquals(custom1, custom2)
    }

    @Test
    fun Custom_with_different_gas_limit_should_not_be_equal() {
        val custom1 = GasParams.Custom(BigInteger("20000000000"), BigInteger("21000"))
        val custom2 = GasParams.Custom(BigInteger("20000000000"), BigInteger("50000"))
        assertNotEquals(custom1, custom2)
    }

    @Test
    fun Custom_allows_zero_gas_price() {
        val custom = GasParams.Custom(BigInteger.ZERO, BigInteger("21000"))
        assertEquals(BigInteger.ZERO, custom.gasPrice)
    }

    @Test
    fun Custom_allows_zero_gas_limit() {
        val custom = GasParams.Custom(BigInteger("20000000000"), BigInteger.ZERO)
        assertEquals(BigInteger.ZERO, custom.gasLimit)
    }

    @Test
    fun Custom_allows_large_gas_values() {
        val largeGasPrice = BigInteger("1000000000000000000") // 1 ETH in wei
        val largeGasLimit = BigInteger("10000000")
        val custom = GasParams.Custom(largeGasPrice, largeGasLimit)
        assertEquals(largeGasPrice, custom.gasPrice)
        assertEquals(largeGasLimit, custom.gasLimit)
    }
}
