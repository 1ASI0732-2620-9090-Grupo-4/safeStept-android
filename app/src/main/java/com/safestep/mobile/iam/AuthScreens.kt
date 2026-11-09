package com.safestep.mobile.iam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.safestep.mobile.R
import com.safestep.mobile.ui.LocalContainer

@Composable
private fun AuthColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.pilot_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun fieldMessage(errors: Set<FieldError>, vararg handled: FieldError): String? =
    handled.firstOrNull { it in errors }?.let {
        when (it) {
            FieldError.UsernameTooShort -> stringResource(R.string.error_username_short)
            FieldError.UsernameTooLong -> stringResource(R.string.error_username_long)
            FieldError.PasswordTooShort -> stringResource(R.string.error_password_short)
            FieldError.PasswordsDiffer -> stringResource(R.string.error_passwords_differ)
            FieldError.TokenMissing -> stringResource(R.string.error_token_missing)
        }
    }

@Composable
private fun authViewModel(): AuthViewModel {
    val container = LocalContainer.current
    return viewModel(factory = viewModelFactory { initializer { AuthViewModel(container.auth) } })
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun UsernameField(value: String, onChange: (String) -> Unit, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.username)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().testTag("username"),
    )
}

@Composable
private fun ErrorLine(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("auth_error"))
}

@Composable
private fun BusyButton(text: String, busy: Boolean, onClick: () -> Unit, tag: String) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        if (busy) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp) else Text(text)
    }
}

@Composable
fun LoginScreen(onRegister: () -> Unit, onForgot: () -> Unit) {
    val model = authViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    AuthColumn {
        Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.titleLarge)
        UsernameField(username, { username = it; model.clearError() }, fieldMessage(state.fieldErrors, FieldError.UsernameTooShort, FieldError.UsernameTooLong))
        PasswordField(password, { password = it; model.clearError() }, stringResource(R.string.password), fieldMessage(state.fieldErrors, FieldError.PasswordTooShort), "password")
        ErrorLine(state.error)
        BusyButton(stringResource(R.string.sign_in), state.busy, { model.signIn(username, password) }, "sign_in")
        TextButton(onClick = onForgot, modifier = Modifier.testTag("go_forgot")) { Text(stringResource(R.string.forgot_password)) }
        TextButton(onClick = onRegister, modifier = Modifier.testTag("go_register")) { Text(stringResource(R.string.no_account)) }
    }
}

@Composable
fun RegisterScreen(onLogin: () -> Unit) {
    val model = authViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    AuthColumn {
        Text(stringResource(R.string.sign_up_title), style = MaterialTheme.typography.titleLarge)
        UsernameField(username, { username = it; model.clearError() }, fieldMessage(state.fieldErrors, FieldError.UsernameTooShort, FieldError.UsernameTooLong))
        PasswordField(password, { password = it; model.clearError() }, stringResource(R.string.password), fieldMessage(state.fieldErrors, FieldError.PasswordTooShort), "password")
        PasswordField(confirmation, { confirmation = it; model.clearError() }, stringResource(R.string.confirm_password), fieldMessage(state.fieldErrors, FieldError.PasswordsDiffer), "confirm_password")
        Text(stringResource(R.string.terms_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ErrorLine(state.error)
        BusyButton(stringResource(R.string.sign_up), state.busy, { model.signUp(username, password, confirmation) }, "sign_up")
        TextButton(onClick = onLogin, modifier = Modifier.testTag("go_login")) { Text(stringResource(R.string.have_account)) }
    }
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    val model = authViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.resetToken) { if (state.resetToken.isNotBlank()) token = state.resetToken }
    AuthColumn {
        Text(stringResource(R.string.forgot_password), style = MaterialTheme.typography.titleLarge)
        when {
            state.resetDone -> {
                Text(stringResource(R.string.reset_done))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back_to_sign_in)) }
            }
            !state.resetRequested -> {
                Text(stringResource(R.string.reset_request_hint))
                UsernameField(username, { username = it; model.clearError() }, fieldMessage(state.fieldErrors, FieldError.UsernameTooShort, FieldError.UsernameTooLong))
                ErrorLine(state.error)
                BusyButton(stringResource(R.string.request_reset), state.busy, { model.requestReset(username) }, "request_reset")
                TextButton(onClick = onBack, modifier = Modifier.testTag("back_to_login")) { Text(stringResource(R.string.back_to_sign_in)) }
            }
            else -> {
                Text(stringResource(R.string.reset_token_hint))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.reset_token)) },
                    singleLine = true,
                    isError = FieldError.TokenMissing in state.fieldErrors,
                    modifier = Modifier.fillMaxWidth().testTag("reset_token"),
                )
                PasswordField(password, { password = it }, stringResource(R.string.new_password), fieldMessage(state.fieldErrors, FieldError.PasswordTooShort), "new_password")
                PasswordField(confirmation, { confirmation = it }, stringResource(R.string.confirm_password), fieldMessage(state.fieldErrors, FieldError.PasswordsDiffer), "confirm_password")
                ErrorLine(state.error)
                BusyButton(stringResource(R.string.change_password), state.busy, { model.resetPassword(token, password, confirmation) }, "change_password")
            }
        }
    }
}
