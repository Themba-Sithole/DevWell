package za.co.devwell.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import za.co.devwell.BuildConfig
import za.co.devwell.R
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
    var page by rememberSaveable { mutableStateOf("splash") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Credentials must never be stored in saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    val registering = page == "register"
    fun changePage(next: String) {
        page = next
        password = ""
        confirmation = ""
        showPassword = false
        onClearFeedback()
    }
    BackHandler(page != "splash" && !state.busy) { changePage("splash") }
    if (page == "splash") {
        SplashScreen({ changePage("register") }, { changePage("login") })
        return
    }
    Column(Modifier.fillMaxSize().background(Color.White).imePadding()
        .verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF1C3823), AuthGreen)))
            .statusBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Brand(Modifier.background(AuthBackground, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp))
            Spacer(Modifier.height(18.dp))
            Text("DevWell", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Well-being for developers,\nby developers.", color = Color(0xFFC1D7BD), textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Text("Check in   •   Reflect   •   Grow", color = Color(0xFFBCD2B8), style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (registering) "Create your account" else "Welcome back", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (registering) "Start your well-being journey with DevWell." else "Sign in to your DevWell account", color = AuthMuted, style = MaterialTheme.typography.bodySmall)
            if (registering) AuthField(name, { name = it }, "Full name", "Alex Developer", !state.busy)
            AuthField(email, { email = it }, "Email", "you@company.io", !state.busy, KeyboardType.Email)
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                enabled = !state.busy, shape = AuthShape, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") } })
            if (registering) {
                OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Confirm password") }, singleLine = true,
                    enabled = !state.busy, shape = AuthShape, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation())
                Text("Use at least 8 characters. Your manager will approve your team access.", color = AuthMuted, style = MaterialTheme.typography.bodySmall)
            }
            AuthFeedback(state.error, state.message)
            PrimaryAction(if (state.busy) "Please wait..." else if (registering) "Create account" else "Sign in",
                modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
                if (registering) onSignUp(name, email, password, confirmation) else onSignIn(email, password)
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = { changePage(if (registering) "login" else "register") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (registering) "Already registered? Sign in" else "New here? Create an account")
            }
            Text("Your sign-in is saved securely on this device. Sign out when using a shared phone.", color = AuthMuted, style = MaterialTheme.typography.bodySmall)
            if (BuildConfig.DEBUG) {
                TextButton(onClick = { showPreview = !showPreview }, enabled = !state.busy) { Text("Student preview (debug only)") }
                if (showPreview) {
                    Text("Uses sample data. No real account or database changes.", style = MaterialTheme.typography.bodySmall)
                    UserRole.entries.forEach { role ->
                        OutlinedButton(onClick = { onPreview(role, false) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Preview ${role.name.lowercase()}") }
                    }
                    OutlinedButton(onClick = { onPreview(UserRole.ENGINEER, true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Preview new engineer") }
                }
            }
        }
    }
}

@Composable
private fun AuthField(value: String, onChange: (String) -> Unit, label: String, placeholder: String, enabled: Boolean, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, onChange, label = { Text(label) }, placeholder = { Text(placeholder) },
        singleLine = true, enabled = enabled, shape = AuthShape, keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth())
}

@Composable
fun SplashScreen(onGetStarted: () -> Unit, onLogin: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFDEE9E0)).systemBarsPadding()) {
        val landscapeHeight = (maxHeight - 370.dp).coerceAtLeast(240.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Brand()
                Text("Check in. Reflect.\nBuild sustainably.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = AuthGreen, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Well-being for developers,\nby developers.", color = AuthMuted, textAlign = TextAlign.Center)
            }
            Image(painterResource(R.drawable.devwell_landscape), null, Modifier.fillMaxWidth().height(landscapeHeight), contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter)
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryAction("Get Started", modifier = Modifier.fillMaxWidth(), onClick = onGetStarted)
                Button(onLogin, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = AuthShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AuthGreen)) { Text("I have an account", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
