package com.safestep.mobile.iam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safestep.mobile.core.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a form field was rejected before calling the server. */
enum class FieldError { UsernameTooShort, UsernameTooLong, PasswordTooShort, PasswordsDiffer, TokenMissing }

object AuthValidation {
    fun username(value: String): FieldError? = when {
        value.trim().length < 3 -> FieldError.UsernameTooShort
        value.trim().length > 50 -> FieldError.UsernameTooLong
        else -> null
    }

    fun password(value: String): FieldError? = if (value.length < 8) FieldError.PasswordTooShort else null

    fun confirmation(password: String, confirmation: String): FieldError? =
        if (password != confirmation) FieldError.PasswordsDiffer else null
}

data class AuthUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val fieldErrors: Set<FieldError> = emptySet(),
    val resetToken: String = "",
    val resetRequested: Boolean = false,
    val resetDone: Boolean = false,
)

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val mutable = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutable.asStateFlow()

    fun clearError() = mutable.update { it.copy(error = null, fieldErrors = emptySet()) }

    fun signIn(username: String, password: String) {
        val problems = listOfNotNull(AuthValidation.username(username), AuthValidation.password(password)).toSet()
        if (problems.isNotEmpty()) return mutable.update { it.copy(fieldErrors = problems) }
        run { auth.signIn(username, password) }
    }

    fun signUp(username: String, password: String, confirmation: String) {
        val problems = listOfNotNull(
            AuthValidation.username(username),
            AuthValidation.password(password),
            AuthValidation.confirmation(password, confirmation),
        ).toSet()
        if (problems.isNotEmpty()) return mutable.update { it.copy(fieldErrors = problems) }
        run { auth.signUp(username, password) }
    }

    fun requestReset(username: String) {
        val problem = AuthValidation.username(username)
        if (problem != null) return mutable.update { it.copy(fieldErrors = setOf(problem)) }
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null, fieldErrors = emptySet()) }
            auth.requestPasswordReset(username).fold(
                onSuccess = { response ->
                    mutable.update { it.copy(busy = false, resetRequested = true, resetToken = response.resetToken.orEmpty()) }
                },
                onFailure = { error -> mutable.update { it.copy(busy = false, error = error.message) } },
            )
        }
    }

    fun resetPassword(token: String, password: String, confirmation: String) {
        val problems = buildSet {
            if (token.isBlank()) add(FieldError.TokenMissing)
            AuthValidation.password(password)?.let { add(it) }
            AuthValidation.confirmation(password, confirmation)?.let { add(it) }
        }
        if (problems.isNotEmpty()) return mutable.update { it.copy(fieldErrors = problems) }
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null, fieldErrors = emptySet()) }
            auth.resetPassword(token, password).fold(
                onSuccess = { mutable.update { state -> state.copy(busy = false, resetDone = true) } },
                onFailure = { error -> mutable.update { it.copy(busy = false, error = error.message) } },
            )
        }
    }

    private fun run(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null, fieldErrors = emptySet()) }
            action().fold(
                onSuccess = { mutable.update { state -> state.copy(busy = false) } },
                onFailure = { error -> mutable.update { it.copy(busy = false, error = error.message) } },
            )
        }
    }
}
