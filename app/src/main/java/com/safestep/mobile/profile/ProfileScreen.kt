package com.safestep.mobile.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.safestep.mobile.R
import com.safestep.mobile.core.ProfileRepository
import com.safestep.mobile.core.network.ProfileDto
import com.safestep.mobile.core.network.UpdateProfileRequest
import com.safestep.mobile.ui.LocalContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val profile: ProfileDto? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            // A brand new account has no profile yet and the API answers with a validation error:
            // that case is shown as an empty form rather than as a failure.
            val profile = repository.me().getOrNull()
            mutable.update { it.copy(loading = false, profile = profile) }
        }
    }

    fun save(request: UpdateProfileRequest) {
        viewModelScope.launch {
            mutable.update { it.copy(saving = true, saved = false, error = null) }
            repository.update(request).fold(
                onSuccess = { saved -> mutable.update { it.copy(saving = false, saved = true, profile = saved) } },
                onFailure = { error -> mutable.update { it.copy(saving = false, error = error.message) } },
            )
        }
    }
}

@Composable
fun ProfileScreen(username: String, onSignOut: () -> Unit) {
    val container = LocalContainer.current
    val model: ProfileViewModel = viewModel(factory = viewModelFactory { initializer { ProfileViewModel(container.profile) } })
    val state by model.state.collectAsStateWithLifecycle()
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var street by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var postalCode by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.profile) {
        state.profile?.let {
            val names = it.fullName.orEmpty().trim().split(" ", limit = 2)
            if (firstName.isBlank()) firstName = names.firstOrNull().orEmpty()
            if (lastName.isBlank()) lastName = names.getOrNull(1).orEmpty()
            if (email.isBlank()) email = it.email.orEmpty()
        }
    }
    val complete = listOf(firstName, lastName, email, street, number, city, postalCode, country).all { it.isNotBlank() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.profile_hint), style = MaterialTheme.typography.bodySmall)
        state.profile?.streetAddress?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.saved_address, it), style = MaterialTheme.typography.bodySmall) }
        ProfileField(firstName, { firstName = it }, R.string.first_name, "first_name")
        ProfileField(lastName, { lastName = it }, R.string.last_name, "last_name")
        ProfileField(email, { email = it }, R.string.email, "email")
        ProfileField(street, { street = it }, R.string.street, "street")
        ProfileField(number, { number = it }, R.string.street_number, "street_number")
        ProfileField(city, { city = it }, R.string.city, "city")
        ProfileField(postalCode, { postalCode = it }, R.string.postal_code, "postal_code")
        ProfileField(country, { country = it }, R.string.country, "country")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.saved) Text(stringResource(R.string.profile_saved), color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("profile_saved"))
        Button(
            onClick = { model.save(UpdateProfileRequest(firstName.trim(), lastName.trim(), email.trim(), street.trim(), number.trim(), city.trim(), postalCode.trim(), country.trim())) },
            enabled = complete && !state.saving,
            modifier = Modifier.fillMaxWidth().testTag("save_profile"),
        ) { Text(stringResource(R.string.save)) }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().testTag("sign_out")) { Text(stringResource(R.string.sign_out)) }
    }
}

@Composable
private fun ProfileField(value: String, onChange: (String) -> Unit, label: Int, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
