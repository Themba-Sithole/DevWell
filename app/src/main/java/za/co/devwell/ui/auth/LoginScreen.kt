package za.co.devwell.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import za.co.devwell.model.UserRole

@Composable
fun LoginScreen(onLogin: (UserRole) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("DevWell", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text("Wellbeing for software teams", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(36.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Spacer(Modifier.height(20.dp))
        Text("Sign in", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(10.dp))
        UserRole.entries.forEach { role ->
            Button(onClick = { onLogin(role) }, Modifier.fillMaxWidth()) {
                Text("Continue as ${role.name.lowercase().replaceFirstChar { it.uppercase() }}")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
