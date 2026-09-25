package za.co.devwell.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import za.co.devwell.BuildConfig
import za.co.devwell.model.UserRole
import za.co.devwell.viewmodel.AuthUiState

@Composable
fun LoginScreen(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, String) -> Unit,
    onClearFeedback: () -> Unit,
    onPreview: (UserRole, Boolean) -> Unit,
) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords stay in memory only; rotation never writes them to saved state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("DevWell", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text("Wellbeing for software teams", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(if (registering) "Create your account" else "Welcome back", style = MaterialTheme.typography.headlineSmall)
        if (registering) {
            Text("Register as an engineer. Manager and admin accounts are assigned by your organisation.")
            OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true,
                enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
            enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
            enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") } },
            modifier = Modifier.fillMaxWidth())
        if (registering) {
            OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirm password") }, singleLine = true,
                enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Text("Use at least 8 characters.", style = MaterialTheme.typography.bodySmall)
        }
        AuthFeedback(state.error, state.message)
        Button(onClick = {
            if (registering) onSignUp(name, email, password, confirmation) else onSignIn(email, password)
        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Please wait..." else if (registering) "Create account" else "Sign in")
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = {
            registering = !registering
            password = ""
            confirmation = ""
            onClearFeedback()
        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (registering) "Already registered? Sign in" else "New here? Create an account")
        }
        Text("Your sign-in is saved securely on this device. Sign out when using a shared phone.", style = MaterialTheme.typography.bodySmall)
        if (BuildConfig.DEBUG) {
            HorizontalDivider()
            TextButton(onClick = { showPreview = !showPreview }, enabled = !state.busy) { Text("Student preview (debug only)") }
            if (showPreview) {
                Text("Uses sample data. No real account or database changes.", style = MaterialTheme.typography.bodySmall)
                UserRole.entries.forEach { role ->
                    OutlinedButton(onClick = { onPreview(role, false) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Preview ${role.name.lowercase()}")
                    }
                }
                OutlinedButton(onClick = { onPreview(UserRole.ENGINEER, true) }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()) { Text("Preview new engineer") }
            }
        }
    }
}

@Composable
fun AuthFeedback(error: String?, message: String? = null) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}
