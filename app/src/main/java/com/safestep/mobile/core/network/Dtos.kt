package com.safestep.mobile.core.network

import kotlinx.serialization.Serializable

// ---- identity and access ----

@Serializable
data class CredentialsRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(
    val id: Long = 0,
    val username: String = "",
    val token: String = "",
    val refreshToken: String = "",
    val roles: List<String> = emptyList(),
)

@Serializable
data class SignUpResponse(val id: Long = 0, val username: String = "")

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class ForgotPasswordRequest(val username: String)

@Serializable
data class ForgotPasswordResponse(
    val username: String = "",
    val resetToken: String? = null,
    val expiresAt: String? = null,
    val mode: String? = null,
)

@Serializable
data class ResetPasswordRequest(val resetToken: String, val newPassword: String)

@Serializable
data class MessageDto(val message: String = "")

@Serializable
data class ErrorDto(val code: String? = null, val message: String? = null, val details: String? = null)

// ---- profiles ----

@Serializable
data class ProfileDto(
    val id: Long = 0,
    val fullName: String? = null,
    val email: String? = null,
    val streetAddress: String? = null,
)

@Serializable
data class UpdateProfileRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val street: String,
    val number: String,
    val city: String,
    val postalCode: String,
    val country: String,
)

// ---- simulations ----

@Serializable
data class OptionDto(val id: String = "", val label: String = "", val feedback: String = "")

@Serializable
data class StepDto(
    val id: String = "",
    val prompt: String = "",
    val correctOptionId: String = "",
    val options: List<OptionDto> = emptyList(),
)

@Serializable
data class ProductSuggestionDto(val productId: String = "", val reason: String = "")

@Serializable
data class SimulationDto(
    val id: String = "",
    val title: String = "",
    val emergencyType: String = "",
    val difficulty: String = "",
    val durationMinutes: Int = 0,
    val xpReward: Int = 0,
    val coinReward: Int = 0,
    val imageUrl: String? = null,
    val status: String = "",
    val completion: Int = 0,
    val description: String = "",
    val learningGoals: List<String> = emptyList(),
    val steps: List<StepDto> = emptyList(),
    val productSuggestions: List<ProductSuggestionDto> = emptyList(),
)

@Serializable
data class AttemptErrorDto(val stepNumber: Int, val error: String, val severity: String = "LOW")

@Serializable
data class AttemptRequest(
    val mode: String,
    val startedAt: String,
    val completedAt: String,
    val score: Int,
    val totalSteps: Int,
    val correctSteps: Int,
    val timeElapsed: Long,
    val errors: List<AttemptErrorDto>,
)

@Serializable
data class AttemptDto(
    val id: String = "",
    val scenarioId: String = "",
    val scenarioSlug: String? = null,
    val mode: String = "",
    val startedAt: String? = null,
    val completedAt: String? = null,
    val score: Int = 0,
    val totalSteps: Int = 0,
    val correctSteps: Int = 0,
    val timeElapsed: Long = 0,
)

// ---- gamification ----

@Serializable
data class SummaryDto(
    val username: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    val safeCoins: Int = 0,
    val streak: Int = 0,
    val completedSimulations: Int = 0,
)

@Serializable
data class MissionDto(
    val id: String = "",
    val title: String = "",
    val cadence: String = "",
    val progress: Int = 0,
    val goal: Int = 1,
    val rewardXp: Int = 0,
    val rewardCoins: Int = 0,
    val status: String = "",
    val instructions: String = "",
    val unlockRequirement: String = "",
)

@Serializable
data class BadgeDto(
    val id: String = "",
    val name: String = "",
    val rarity: String = "",
    val unlocked: Boolean = false,
    val description: String = "",
    val unlockRequirement: String = "",
)

@Serializable
data class LeaderboardEntryDto(
    val rank: Int = 0,
    val name: String = "",
    val xp: Int = 0,
    val streak: Int = 0,
)

@Serializable
data class CoinTransactionDto(
    val id: String = "",
    val simulationId: String = "",
    val simulationTitle: String = "",
    val baseCoins: Int = 0,
    val multiplier: Double = 0.0,
    val accuracy: Double = 0.0,
    val earnedCoins: Int = 0,
    val successful: Boolean = false,
    val createdAt: String? = null,
)

// ---- analytics ----

@Serializable
data class MetricDto(val label: String = "", val value: String = "", val trend: String = "")

@Serializable
data class SkillProgressDto(val skill: String = "", val progress: Int = 0)

@Serializable
data class CommonMistakeDto(val topic: String = "", val mistakes: Int = 0, val recommendation: String = "")

@Serializable
data class WeeklyActivityDto(val day: String = "", val xp: Int = 0, val simulations: Int = 0)

@Serializable
data class PerformanceByDifficultyDto(val difficulty: String = "", val completed: Int = 0, val accuracy: Int = 0)

@Serializable
data class AnalyticsSummaryDto(
    val summary: List<MetricDto> = emptyList(),
    val skillProgress: List<SkillProgressDto> = emptyList(),
    val commonMistakes: List<CommonMistakeDto> = emptyList(),
    val weeklyActivity: List<WeeklyActivityDto> = emptyList(),
    val performanceByDifficulty: List<PerformanceByDifficultyDto> = emptyList(),
)

@Serializable
data class CertificateDto(
    val id: Long = 0,
    val moduleName: String = "",
    val score: Int = 0,
    val achievementLevel: String = "",
    val issuedAt: String? = null,
    val verificationCode: String = "",
)

// ---- commerce ----

@Serializable
data class ProductDto(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val type: String = "",
    val price: Double = 0.0,
    val oldPrice: Double? = null,
    val rating: Double = 0.0,
    val stock: Int = 0,
    val imageUrl: String? = null,
    val tags: List<String> = emptyList(),
    val description: String = "",
)

@Serializable
data class CategoryDto(val externalId: String = "", val name: String = "", val productCount: Int = 0)

@Serializable
data class KitDto(
    val externalId: String = "",
    val name: String = "",
    val description: String = "",
    val level: String = "",
    val individualPrice: Double = 0.0,
    val kitPrice: Double = 0.0,
    val savings: Double = 0.0,
    val savingsPercentage: Int = 0,
    val imageUrl: String? = null,
    val popular: Boolean = false,
)

@Serializable
data class RecommendationDto(
    val externalId: String = "",
    val productId: String = "",
    val reason: String = "",
    val relatedSimulationId: String? = null,
    val priority: String = "",
)

@Serializable
data class CartItemDto(
    val id: String = "",
    val productId: String = "",
    val quantity: Int = 1,
)

@Serializable
data class AddCartItemRequest(val productId: String, val quantity: Int)

@Serializable
data class UpdateCartItemRequest(val quantity: Int)

@Serializable
data class CouponDto(
    val id: String = "",
    val title: String = "",
    val costCoins: Int = 0,
    val type: String = "",
    val discountPercentage: Int = 0,
    val minPurchaseAmount: Double? = null,
)

@Serializable
data class RedeemedCouponDto(
    val id: String = "",
    val couponId: String = "",
    val title: String = "",
    val type: String = "",
    val discountPercentage: Int = 0,
    val minPurchaseAmount: Double? = null,
    val redeemedAt: String? = null,
    val usedAt: String? = null,
    val status: String = "",
)

@Serializable
data class CreateOrderRequest(val status: String = "PENDING", val redeemedCouponExternalId: String? = null)

@Serializable
data class OrderItemDto(
    val productId: String = "",
    val productName: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Int = 1,
)

@Serializable
data class OrderDto(
    val id: String = "",
    val total: Double = 0.0,
    val status: String = "",
    val paymentProvider: String? = null,
    val paymentStatus: String? = null,
    val items: List<OrderItemDto> = emptyList(),
    val createdAt: String? = null,
    val finalTotal: Double? = null,
    val appliedDiscountPercentage: Int? = null,
    val redeemedCouponExternalId: String? = null,
)

@Serializable
data class StripeCheckoutDto(val sessionUrl: String = "", val sessionId: String = "")
