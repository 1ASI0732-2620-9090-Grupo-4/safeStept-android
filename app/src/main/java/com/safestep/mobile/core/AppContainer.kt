package com.safestep.mobile.core

import android.content.Context
import com.safestep.mobile.BuildConfig
import com.safestep.mobile.core.network.ApiFactory
import com.safestep.mobile.core.session.SecureTokenStore
import com.safestep.mobile.core.session.SessionManager
import com.safestep.mobile.simulation.SimulationRepository

/** Wires the whole app by hand: there are few enough pieces that a DI framework would only add noise. */
class AppContainer(
    val sessions: SessionManager,
    val assetsBaseUrl: String,
    apiBaseUrl: String,
) {
    private val api = ApiFactory(apiBaseUrl, sessions)

    val auth = AuthRepository(api.auth, sessions)
    val profile = ProfileRepository(api.profile)
    val simulations = SimulationRepository(api.simulation, api.gamification)
    val gamification = GamificationRepository(api.gamification, api.analytics)
    val commerce = CommerceRepository(api.commerce)

    companion object {
        fun create(context: Context): AppContainer =
            AppContainer(
                sessions = SessionManager(SecureTokenStore(context)),
                assetsBaseUrl = BuildConfig.ASSETS_BASE_URL,
                apiBaseUrl = BuildConfig.API_BASE_URL,
            )
    }
}
