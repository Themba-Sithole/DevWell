package za.co.devwell.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import za.co.devwell.R
import za.co.devwell.model.SignupRole
import za.co.devwell.ui.components.DevWellTextField
import za.co.devwell.ui.components.devWellScrollbar
import za.co.devwell.ui.theme.*
import za.co.devwell.viewmodel.AuthUiState

@Composable
fun LoginScreen(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, String, SignupRole) -> Unit,
    onClearFeedback: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf("splash") }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Credentials must never be stored in saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var role by rememberSaveable { mutableStateOf(SignupRole.ENGINEER) }
    val pageScroll = rememberScrollState()
    fun changePage(next: String) {
        page = next
        password = ""
        confirmation = ""
        showPassword = false
        onClearFeedback()
    }
    BackHandler(page != "splash" && !state.busy) { changePage("splash") }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = authPageOrder(initialState) < authPageOrder(targetState)
            val enter = slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { width ->
                if (forward) width / 5 else -width / 5
            } + fadeIn(tween(320, easing = FastOutSlowInEasing))
            val exit = slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { width ->
                if (forward) -width / 5 else width / 5
            } + fadeOut(tween(200, easing = FastOutSlowInEasing))
            enter togetherWith exit
        },
        label = "loginPage",
        modifier = Modifier.fillMaxSize().clipToBounds(),
    ) { current ->
        if (current == "splash") {
            SplashScreen({ changePage("register") }, { changePage("login") })
        } else {
            val registering = current == "register"
            Column(Modifier.fillMaxSize().background(Color.White).imePadding()
                .devWellScrollbar(pageScroll)
                .verticalScroll(pageScroll)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(LoginGradient))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 32.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Brand(
                    Modifier
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text("DevWell", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Well-being for developers,\nby developers.",
                    color = Color(0xFFC8DCCE), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text(
                        "Check in   ·   Reflect   ·   Grow",
                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth().offset(y = (-24).dp),
            ) {
                Column(Modifier.padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 24.dp)
                    .navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (registering) "Create your account" else "Welcome back",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (registering) "Start your well-being journey with DevWell." else "Sign in to your DevWell account",
                        color = AuthMuted, style = MaterialTheme.typography.bodyMedium,
                    )
                    if (registering) AuthField(name, { name = it }, "Full name", "Alex Developer", !state.busy)
                    AuthField(email, { email = it }, "Email", "you@company.io", !state.busy, KeyboardType.Email)
                    DevWellTextField(
                        password, { password = it }, label = "Password", singleLine = true,
                        enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "Hide" else "Show", color = AuthMuted)
                            }
                        },
                    )
                    if (registering) {
                        RolePicker(role, !state.busy) { role = it }
                        DevWellTextField(
                            confirmation, { confirmation = it }, label = "Confirm password", singleLine = true,
                            enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        Text(
                            if (role == SignupRole.MANAGER) {
                                "Use at least 8 characters. You will create your company and first team next."
                            } else {
                                "Use at least 8 characters. Your manager will approve your team access."
                            },
                            color = AuthMuted, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    AuthFeedback(state.error, state.message)
                    PrimaryAction(
                        if (state.busy) "Please wait..." else if (registering) "Create account" else "Sign in",
                        modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                    ) {
                        if (registering) onSignUp(name, email, password, confirmation, role) else onSignIn(email, password)
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = { changePage(if (registering) "login" else "register") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (registering) "Already registered? Sign in" else "New here? Create an account", color = AuthGreen)
                    }
                    Text(
                        "Your sign-in is saved securely on this device. Sign out when using a shared phone.",
                        color = AuthMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

private fun authPageOrder(page: String): Int = when (page) {
    "splash" -> 0
    "login" -> 1
    else -> 2
}

/**
 * Lets a new account choose between joining a team and leading one. The choice
 * is a request only: the database grants the final role and never accepts admin.
 */
@Composable
private fun RolePicker(role: SignupRole, enabled: Boolean, onSelect: (SignupRole) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("I want to register as", style = MaterialTheme.typography.labelLarge, color = AuthMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoleCard(
                title = "Engineer", subtitle = "Join a team", selected = role == SignupRole.ENGINEER,
                enabled = enabled, modifier = Modifier.weight(1f),
            ) { onSelect(SignupRole.ENGINEER) }
            RoleCard(
                title = "Manager", subtitle = "Create a company", selected = role == SignupRole.MANAGER,
                enabled = enabled, modifier = Modifier.weight(1f),
            ) { onSelect(SignupRole.MANAGER) }
        }
        Text(
            if (role == SignupRole.MANAGER) {
                "You will create your company and first team. Engineers can then request to join it."
            } else {
                "You will pick the company and team you work for. A manager approves your request."
            },
            color = AuthMuted, style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = AuthShape,
        color = if (selected) AccentSurface else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AuthGreen else AuthBorder),
        modifier = modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) AuthGreen else AuthMuted,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AuthMuted,
            )
        }
    }
}

@Composable
private fun AuthField(value: String, onChange: (String) -> Unit, label: String, placeholder: String, enabled: Boolean, keyboard: KeyboardType = KeyboardType.Text) {
    DevWellTextField(
        value, onChange, label = label, placeholder = placeholder, singleLine = true,
        enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

@Composable
fun SplashScreen(onGetStarted: () -> Unit, onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFFDDE8E0)).systemBarsPadding()) {
        Image(
            painterResource(R.drawable.devwell_landscape), null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, alignment = Alignment.Center,
        )
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(56.dp))
            Brand()
            Spacer(Modifier.height(14.dp))
            Text(
                "Check in. Reflect.\nBuild sustainably.",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = AuthGreen, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Well-being for developers, by developers.",
                color = AuthMuted, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xF2DDE8E0)),
                        ),
                    )
                    .padding(20.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryAction("Get Started", modifier = Modifier.fillMaxWidth(), onClick = onGetStarted)
                OutlinedButton(
                    onLogin, Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AuthGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AuthGreen),
                ) { Text("I have an account", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
