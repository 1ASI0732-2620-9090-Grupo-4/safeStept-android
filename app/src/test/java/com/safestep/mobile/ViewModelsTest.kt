package com.safestep.mobile

import com.safestep.mobile.commerce.CartData
import com.safestep.mobile.commerce.CartLine
import com.safestep.mobile.core.network.CartItemDto
import com.safestep.mobile.core.network.OptionDto
import com.safestep.mobile.core.network.ProductDto
import com.safestep.mobile.core.network.RedeemedCouponDto
import com.safestep.mobile.core.network.SimulationDto
import com.safestep.mobile.core.network.StepDto
import com.safestep.mobile.iam.AuthValidation
import com.safestep.mobile.iam.FieldError
import com.safestep.mobile.simulation.SimulationFilters
import com.safestep.mobile.simulation.SimulationListState
import com.safestep.mobile.ui.Load
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewModelsTest {
    @Test
    fun `username and password rules match the API`() {
        assertEquals(FieldError.UsernameTooShort, AuthValidation.username("ab"))
        assertEquals(FieldError.UsernameTooLong, AuthValidation.username("x".repeat(51)))
        assertNull(AuthValidation.username("ana"))
        assertEquals(FieldError.PasswordTooShort, AuthValidation.password("1234567"))
        assertNull(AuthValidation.password("12345678"))
        assertEquals(FieldError.PasswordsDiffer, AuthValidation.confirmation("a", "b"))
        assertNull(AuthValidation.confirmation("a", "a"))
    }

    @Test
    fun `surrounding spaces do not count towards the username length`() {
        assertEquals(FieldError.UsernameTooShort, AuthValidation.username("  a  "))
    }

    private fun sim(id: String, title: String, type: String, difficulty: String) = SimulationDto(
        id = id,
        title = title,
        emergencyType = type,
        difficulty = difficulty,
        steps = listOf(StepDto("$id-1", "?", "a", listOf(OptionDto("a", "A")))),
    )

    @Test
    fun `simulation filters combine search, type and difficulty`() {
        val all = listOf(
            sim("rcp", "RCP para adultos", "RCP", "BASIC"),
            sim("rcp-adv", "RCP avanzado", "RCP", "ADVANCED"),
            sim("burn", "Quemaduras", "Quemaduras", "BASIC"),
        )
        val ready = SimulationListState(load = Load.Ready(all))

        assertEquals(3, ready.visible.size)
        assertEquals(listOf("Quemaduras", "RCP"), ready.emergencyTypes)
        assertEquals(2, ready.copy(filters = SimulationFilters(emergencyType = "RCP")).visible.size)
        assertEquals(listOf("rcp"), ready.copy(filters = SimulationFilters(emergencyType = "RCP", difficulty = "BASIC")).visible.map { it.id })
        assertEquals(listOf("burn"), ready.copy(filters = SimulationFilters(query = "quema")).visible.map { it.id })
        assertTrue(ready.copy(filters = SimulationFilters(query = "nada")).visible.isEmpty())
    }

    private fun product(id: String, price: Double) = ProductDto(id = id, name = id, price = price)

    private fun coupon(id: String, percent: Int, min: Double?, status: String = "AVAILABLE") =
        RedeemedCouponDto(id = id, couponId = "c-$id", title = id, discountPercentage = percent, minPurchaseAmount = min, status = status)

    @Test
    fun `cart totals and coupon discount`() {
        val lines = listOf(
            CartLine(CartItemDto("i1", "kit", 2), product("kit", 50.0)),
            CartLine(CartItemDto("i2", "mask", 1), product("mask", 20.0)),
        )
        val data = CartData(lines, listOf(coupon("five", 5, null), coupon("ten-min-200", 10, 200.0), coupon("used", 15, null, "USED")))

        assertEquals(120.0, data.total, 0.001)
        assertEquals(listOf("five"), data.applicableCoupons.map { it.id })
        assertEquals(114.0, data.totalWith("five"), 0.001)
        assertEquals(120.0, data.totalWith(null), 0.001)
        // a coupon that does not apply to the cart is ignored instead of discounting it
        assertEquals(120.0, data.totalWith("ten-min-200"), 0.001)
    }

    @Test
    fun `a coupon with a minimum purchase applies once the cart reaches it`() {
        val lines = listOf(CartLine(CartItemDto("i1", "kit", 3), product("kit", 100.0)))
        val data = CartData(lines, listOf(coupon("ten-min-200", 10, 200.0)))

        assertEquals(listOf("ten-min-200"), data.applicableCoupons.map { it.id })
        assertEquals(270.0, data.totalWith("ten-min-200"), 0.001)
    }

    @Test
    fun `a cart line without a known product contributes nothing`() {
        assertEquals(0.0, CartLine(CartItemDto("i1", "gone", 3), null).subtotal, 0.001)
    }
}
