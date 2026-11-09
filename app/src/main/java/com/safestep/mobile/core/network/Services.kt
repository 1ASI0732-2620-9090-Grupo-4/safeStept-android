package com.safestep.mobile.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthService {
    @POST("authentication/sign-in")
    suspend fun signIn(@Body body: CredentialsRequest): AuthResponse

    @POST("authentication/sign-up")
    suspend fun signUp(@Body body: CredentialsRequest): SignUpResponse

    @POST("authentication/refresh-token")
    suspend fun refresh(@Body body: RefreshTokenRequest): AuthResponse

    @POST("authentication/logout")
    suspend fun logout(@Body body: RefreshTokenRequest): MessageDto

    @POST("authentication/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): ForgotPasswordResponse

    @POST("authentication/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): MessageDto
}

interface ProfileService {
    @GET("profiles/me")
    suspend fun me(): ProfileDto

    @PUT("profiles/me")
    suspend fun update(@Body body: UpdateProfileRequest): ProfileDto
}

interface SimulationService {
    @GET("simulations")
    suspend fun list(): List<SimulationDto>

    @GET("simulations/{id}")
    suspend fun detail(@Path("id") id: String): SimulationDto

    @POST("simulations/{id}/attempts")
    suspend fun submitAttempt(@Path("id") id: String, @Body body: AttemptRequest): AttemptDto

    @GET("simulations/attempts/me")
    suspend fun myAttempts(): List<AttemptDto>
}

interface GamificationService {
    @GET("gamification/summary/me")
    suspend fun summary(): SummaryDto

    @GET("gamification/missions")
    suspend fun missions(): List<MissionDto>

    @GET("gamification/badges/me")
    suspend fun badges(): List<BadgeDto>

    @GET("gamification/leaderboard")
    suspend fun leaderboard(): List<LeaderboardEntryDto>

    @GET("gamification/coin-transactions/me")
    suspend fun coinTransactions(): List<CoinTransactionDto>
}

interface AnalyticsService {
    @GET("analytics/summary/me")
    suspend fun summary(): AnalyticsSummaryDto

    @GET("analytics/certificates/me")
    suspend fun certificates(): List<CertificateDto>
}

interface CommerceService {
    @GET("commerce/products")
    suspend fun products(@Query("q") query: String? = null): List<ProductDto>

    @GET("commerce/products/{id}")
    suspend fun product(@Path("id") id: String): ProductDto

    @GET("commerce/categories")
    suspend fun categories(): List<CategoryDto>

    @GET("commerce/kits")
    suspend fun kits(): List<KitDto>

    @GET("commerce/recommendations/me")
    suspend fun recommendations(): List<RecommendationDto>

    @GET("commerce/cart/me")
    suspend fun cart(): List<CartItemDto>

    @POST("commerce/cart/items")
    suspend fun addToCart(@Body body: AddCartItemRequest): CartItemDto

    @PUT("commerce/cart/items/{id}")
    suspend fun updateCartItem(@Path("id") id: String, @Body body: UpdateCartItemRequest): CartItemDto

    @DELETE("commerce/cart/items/{id}")
    suspend fun removeCartItem(@Path("id") id: String)

    @GET("commerce/coupons")
    suspend fun coupons(): List<CouponDto>

    @POST("commerce/coupons/{id}/redeem")
    suspend fun redeemCoupon(@Path("id") id: String, @Body body: Map<String, String> = emptyMap()): RedeemedCouponDto

    @GET("commerce/coupons/redeemed/me")
    suspend fun redeemedCoupons(): List<RedeemedCouponDto>

    @GET("commerce/orders/me")
    suspend fun orders(): List<OrderDto>

    @POST("commerce/orders")
    suspend fun createOrder(@Body body: CreateOrderRequest): OrderDto

    @POST("commerce/orders/{id}/payments/stripe-checkout")
    suspend fun stripeCheckout(@Path("id") id: String): StripeCheckoutDto

    @POST("commerce/orders/{id}/payments/stripe-confirm")
    suspend fun stripeConfirm(@Path("id") id: String, @Query("sessionId") sessionId: String): OrderDto

    @POST("commerce/orders/{id}/payments/stripe-cancel")
    suspend fun stripeCancel(@Path("id") id: String, @Query("sessionId") sessionId: String? = null): OrderDto
}
