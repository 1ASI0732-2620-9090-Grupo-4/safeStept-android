package com.safestep.mobile.core

import com.safestep.mobile.core.network.AddCartItemRequest
import com.safestep.mobile.core.network.AnalyticsService
import com.safestep.mobile.core.network.AuthService
import com.safestep.mobile.core.network.CommerceService
import com.safestep.mobile.core.network.CreateOrderRequest
import com.safestep.mobile.core.network.CredentialsRequest
import com.safestep.mobile.core.network.ForgotPasswordRequest
import com.safestep.mobile.core.network.GamificationService
import com.safestep.mobile.core.network.ProfileService
import com.safestep.mobile.core.network.RefreshTokenRequest
import com.safestep.mobile.core.network.ResetPasswordRequest
import com.safestep.mobile.core.network.UpdateCartItemRequest
import com.safestep.mobile.core.network.UpdateProfileRequest
import com.safestep.mobile.core.network.apiCall
import com.safestep.mobile.core.session.Session
import com.safestep.mobile.core.session.SessionManager

class AuthRepository(private val api: AuthService, private val sessions: SessionManager) {
    suspend fun signIn(username: String, password: String): Result<Unit> =
        apiCall { api.signIn(CredentialsRequest(username.trim(), password)) }.map {
            sessions.start(Session(it.username.ifBlank { username.trim() }, it.token, it.refreshToken, it.roles))
        }

    /** Creates the account and signs in right away, so the player lands inside the app. */
    suspend fun signUp(username: String, password: String): Result<Unit> {
        val created = apiCall { api.signUp(CredentialsRequest(username.trim(), password)) }
        if (created.isFailure) return Result.failure(created.exceptionOrNull()!!)
        return signIn(username, password)
    }

    suspend fun signOut() {
        sessions.refreshToken?.let { token -> apiCall { api.logout(RefreshTokenRequest(token)) } }
        sessions.end()
    }

    suspend fun requestPasswordReset(username: String) = apiCall { api.forgotPassword(ForgotPasswordRequest(username.trim())) }

    suspend fun resetPassword(token: String, newPassword: String) =
        apiCall { api.resetPassword(ResetPasswordRequest(token.trim(), newPassword)) }
}

class ProfileRepository(private val api: ProfileService) {
    suspend fun me() = apiCall { api.me() }

    suspend fun update(request: UpdateProfileRequest) = apiCall { api.update(request) }
}

class GamificationRepository(private val api: GamificationService, private val analytics: AnalyticsService) {
    suspend fun summary() = apiCall { api.summary() }
    suspend fun missions() = apiCall { api.missions() }
    suspend fun badges() = apiCall { api.badges() }
    suspend fun leaderboard() = apiCall { api.leaderboard() }
    suspend fun coinTransactions() = apiCall { api.coinTransactions() }
    suspend fun statistics() = apiCall { analytics.summary() }
    suspend fun certificates() = apiCall { analytics.certificates() }
}

class CommerceRepository(private val api: CommerceService) {
    suspend fun products() = apiCall { api.products() }
    suspend fun product(id: String) = apiCall { api.product(id) }
    suspend fun categories() = apiCall { api.categories() }
    suspend fun kits() = apiCall { api.kits() }
    suspend fun recommendations() = apiCall { api.recommendations() }
    suspend fun cart() = apiCall { api.cart() }
    suspend fun addToCart(productId: String, quantity: Int = 1) = apiCall { api.addToCart(AddCartItemRequest(productId, quantity)) }
    suspend fun setQuantity(itemId: String, quantity: Int) = apiCall { api.updateCartItem(itemId, UpdateCartItemRequest(quantity)) }
    suspend fun removeFromCart(itemId: String) = apiCall { api.removeCartItem(itemId) }
    suspend fun coupons() = apiCall { api.coupons() }
    suspend fun redeemCoupon(couponId: String) = apiCall { api.redeemCoupon(couponId) }
    suspend fun redeemedCoupons() = apiCall { api.redeemedCoupons() }
    suspend fun orders() = apiCall { api.orders() }
    suspend fun createOrder(redeemedCouponId: String?) =
        apiCall { api.createOrder(CreateOrderRequest(redeemedCouponExternalId = redeemedCouponId)) }
    suspend fun startStripeCheckout(orderId: String) = apiCall { api.stripeCheckout(orderId) }
    suspend fun confirmStripePayment(orderId: String, sessionId: String) = apiCall { api.stripeConfirm(orderId, sessionId) }
    suspend fun cancelStripePayment(orderId: String, sessionId: String?) = apiCall { api.stripeCancel(orderId, sessionId) }
}
