package com.safestep.mobile.core.network

import com.safestep.mobile.core.session.Session
import com.safestep.mobile.core.session.SessionManager
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Failure of a call to the API, with a message that can be shown to the user. */
class ApiException(
    override val message: String,
    val status: Int = 0,
    val code: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

val apiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/**
 * Runs an API call and turns every failure into an [ApiException] so that screens only deal with
 * one error type.
 */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: HttpException) {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val parsed = runCatching { apiJson.decodeFromString<ErrorDto>(body) }.getOrNull()
        val message = parsed?.details?.takeIf { it.isNotBlank() }
            ?: parsed?.message?.takeIf { it.isNotBlank() }
            ?: "HTTP ${error.code()}"
        Result.failure(ApiException(message, error.code(), parsed?.code, error))
    } catch (error: IOException) {
        Result.failure(ApiException("Network error", cause = error))
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(ApiException(error.message ?: "Unexpected error", cause = error))
    }

/** Builds the Retrofit services that talk to the SafeStep REST API. */
class ApiFactory(baseUrl: String, private val sessions: SessionManager, okHttp: OkHttpClient = OkHttpClient()) {
    private val root = baseUrl.trim().trimEnd('/') + "/"

    // Used only to rotate tokens: it must not carry the authenticator that calls it.
    private val plainClient = okHttp.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val authService: AuthService = retrofit(plainClient).create(AuthService::class.java)

    private val client = plainClient.newBuilder()
        .addInterceptor(BearerInterceptor(sessions))
        .authenticator(TokenRefreshAuthenticator(sessions, authService))
        .build()

    private val retrofit = retrofit(client)

    /** Service for public calls (sign in, sign up, password reset). */
    val auth: AuthService get() = authService
    val profile: ProfileService by lazy { retrofit.create(ProfileService::class.java) }
    val simulation: SimulationService by lazy { retrofit.create(SimulationService::class.java) }
    val gamification: GamificationService by lazy { retrofit.create(GamificationService::class.java) }
    val analytics: AnalyticsService by lazy { retrofit.create(AnalyticsService::class.java) }
    val commerce: CommerceService by lazy { retrofit.create(CommerceService::class.java) }

    private fun retrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(root)
            .client(client)
            .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
            .build()
}

private class BearerInterceptor(private val sessions: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessions.accessToken
        val request = if (token.isNullOrBlank() || chain.request().header("Authorization") != null) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }
}

/** On a 401 it exchanges the refresh token once and retries; if that fails the session ends. */
private class TokenRefreshAuthenticator(
    private val sessions: SessionManager,
    private val auth: AuthService,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains("/authentication/")) return null
        if (responseCount(response) >= 2) return null
        synchronized(this) {
            val current = sessions.session.value ?: return null
            val usedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            // Another call already refreshed the token while this one was waiting.
            if (usedToken != null && usedToken != current.accessToken) {
                return response.request.newBuilder().header("Authorization", "Bearer ${current.accessToken}").build()
            }
            val renewed = runCatching {
                kotlinx.coroutines.runBlocking { auth.refresh(RefreshTokenRequest(current.refreshToken)) }
            }.getOrNull()
            if (renewed == null || renewed.token.isBlank()) {
                sessions.end()
                return null
            }
            sessions.start(
                Session(
                    username = renewed.username.ifBlank { current.username },
                    accessToken = renewed.token,
                    refreshToken = renewed.refreshToken.ifBlank { current.refreshToken },
                    roles = renewed.roles.ifEmpty { current.roles },
                ),
            )
            return response.request.newBuilder().header("Authorization", "Bearer ${renewed.token}").build()
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
