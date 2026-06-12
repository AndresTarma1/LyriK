package com.example.melodist.ui.screens

import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.melodist.navigation.Route
import com.metrolist.innertube.YouTube
import com.example.melodist.ui.components.MelodistImage
import com.example.melodist.ui.components.PlaceholderType
import com.example.melodist.ui.utils.circleAwareShape
import com.example.melodist.utils.LocalPlayerViewModel
import com.example.melodist.viewmodels.AccountState
import com.example.melodist.viewmodels.AccountViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.metrolist.innertube.models.PlaylistItem
import com.example.melodist.data.account.BrowserCookieExtractor
import com.example.melodist.data.account.BrowserLoginHelper
import com.example.melodist.data.account.BrowserProfile
import com.example.melodist.data.account.CookieExtractResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AccountScreenState(
    val uiState: AccountState = AccountState.NotLoggedIn,
    val cookieInput: String = "",
    val cookieWarnings: List<String> = emptyList(),
)

data class AccountActions(
    val onCookieInputChange: (String) -> Unit,
    val onLogin: () -> Unit,
    val onLogout: () -> Unit,
    val onReset: () -> Unit,
    val onRetry: () -> Unit,
    val onRefreshPlaylists: () -> Unit,
    val onNavigate: (Route) -> Unit,
    val onLoginWithCookie: (String) -> Unit = {},
)


@Composable
fun AccountScreenRoute(
    viewModel: AccountViewModel,
    onNavigate: (Route) -> Unit,
) {

    val playerViewModel = LocalPlayerViewModel.current

    val uiState by viewModel.uiState.collectAsState()
    val cookieInput by viewModel.cookieInput.collectAsState()
    val cookieWarnings by viewModel.cookieWarnings.collectAsState()

    val state = AccountScreenState(
        uiState = uiState,
        cookieInput = cookieInput,
        cookieWarnings = cookieWarnings,
    )

    val actions = remember(viewModel, onNavigate) {
        AccountActions(
            onCookieInputChange = { viewModel.onCookieInputChange(it) },
            onLoginWithCookie = { viewModel.loginWithCookie(it) },
            onLogin = {
                val raw = viewModel.cookieInput.value
                // Detectar y parsear formato Metrolist: ***INNERTUBE COOKIE*** =xxx
                val metrolistRegex = """\*\*\*INNERTUBE COOKIE\*\*\*\s*=([\s\S]*?)(?:\n\*\*\*|\z)""".toRegex()
                val match = metrolistRegex.find(raw)
                if (match != null) {
                    val parsed = match.groupValues[1].trim()
                    // Extraer visitorData
                    """\*\*\*VISITOR DATA\*\*\*\s*=([^\n]+)""".toRegex().find(raw)?.let {
                        val vd = it.groupValues[1].trim()
                        if (vd.isNotEmpty()) YouTube.visitorData = vd
                    }
                    // Extraer dataSyncId
                    """\*\*\*DATASYNC ID\*\*\*\s*=([^\n]+)""".toRegex().find(raw)?.let {
                        val ds = it.groupValues[1].trim()
                        if (ds.isNotEmpty()) YouTube.dataSyncId = ds
                    }
                    if (parsed.isNotEmpty()) viewModel.onCookieInputChange(parsed)
                }
                viewModel.login()
            },
            onLogout = { viewModel.logout() },
            onReset = { viewModel.reset() },
            onRetry = { viewModel.retry() },
            onRefreshPlaylists = { viewModel.refreshPlaylists() },
            onNavigate = onNavigate,
        )
    }

    AccountScreen(
        state = state,
        actions = actions,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    state: AccountScreenState,
    actions: AccountActions,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.account_title),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp)
                    )
                },
                actions = {
                    if (state.uiState is AccountState.LoggedIn) {
                        IconButton(
                            onClick = actions.onLogout,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(Res.string.logout),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = state.uiState,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "accountContent"
            ) { contentState ->
                when (contentState) {
                    is AccountState.NotLoggedIn -> LoginSection(
                        cookieInput = state.cookieInput,
                        cookieWarnings = state.cookieWarnings,
                        onCookieInputChange = actions.onCookieInputChange,
                        onLogin = actions.onLogin,
                        onLoginWithCookie = actions.onLoginWithCookie
                    )
                    is AccountState.Loading -> LoadingSection()
                    is AccountState.LoggedIn -> LoggedInSection(
                        state = contentState,
                        onRefresh = actions.onRefreshPlaylists,
                        onNavigate = actions.onNavigate,
                    )
                    is AccountState.Error -> ErrorSection(
                        message = contentState.message,
                        onRetry = actions.onRetry,
                        onReset = actions.onReset
                    )
                    is AccountState.CookieExpired -> CookieExpiredSection(
                        cookieInput = state.cookieInput,
                        cookieWarnings = state.cookieWarnings,
                        onCookieInputChange = actions.onCookieInputChange,
                        onRenew = actions.onLogin,
                        onLogout = actions.onLogout
                    )
                }
            }
        }
    }
}


@Composable
private fun LoginSection(
    cookieInput: String,
    cookieWarnings: List<String> = emptyList(),
    onCookieInputChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLoginWithCookie: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showCookie by remember { mutableStateOf(false) }
    var browsers by remember { mutableStateOf<List<BrowserProfile>>(emptyList()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var browserLoginStep by remember { mutableStateOf<String?>(null) }

    val hasBrowser = remember { BrowserLoginHelper.findBrowserExecutable() != null }

    LaunchedEffect(Unit) {
        browsers = withContext(Dispatchers.IO) {
            BrowserCookieExtractor.detectBrowsers()
        }
    }

    fun handleCookieResult(result: CookieExtractResult) {
        browserLoginStep = null
        when (result) {
            is CookieExtractResult.Success -> {
                onLoginWithCookie(result.cookie)
            }
            is CookieExtractResult.Error -> {
                browserLoginStep = "Error: ${result.message}"
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(84.dp).clip(circleAwareShape()).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(stringResource(Res.string.login_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.login_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // ── Browser sign in ─────────────────────────────────────────
            if (hasBrowser) {
                Card(
                    onClick = {
                        scope.launch {
                            browserLoginStep = "Opening browser..."
                            val result = BrowserLoginHelper.loginWithBrowser { status ->
                                browserLoginStep = status
                            }
                            handleCookieResult(result)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Sign in with browser",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Opens a browser window. Sign in to YouTube Music, then close it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.Login,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (browserLoginStep != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(browserLoginStep ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Import from browser ─────────────────────────────────────
            if (browsers.isNotEmpty()) {
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Import from existing browser session")
                }

                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Pick a browser where you're already signed in to YouTube Music.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        browsers.forEach { browser ->
                            Card(
                                onClick = {
                                    scope.launch {
                                        browserLoginStep = "Reading cookies from ${browser.name}..."
                                        val result = withContext(Dispatchers.IO) {
                                            BrowserCookieExtractor.extractYouTubeCookies(browser)
                                        }
                                        handleCookieResult(result)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Language,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(browser.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            browser.userDataDir.absolutePath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.Login,
                                        contentDescription = "Import",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Divider ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    " or paste cookie manually ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // ── Manual cookie paste (existing) ─────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.need_metrolist), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    }

                    HelpStep("1", stringResource(Res.string.step1))
                    HelpStep("2", stringResource(Res.string.step2))
                    HelpStep("3", stringResource(Res.string.step3))
                    HelpStep("4", stringResource(Res.string.step4))

                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.token_example), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ) {
                        Text(
                            stringResource(Res.string.token_example_value),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = cookieInput,
                onValueChange = onCookieInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.cookie_label)) },
                placeholder = { Text(stringResource(Res.string.cookie_placeholder), style = MaterialTheme.typography.bodySmall) },
                visualTransformation = if (showCookie) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (cookieInput.isNotBlank()) onLogin() }),
                trailingIcon = {
                    IconButton(onClick = { showCookie = !showCookie }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                        Icon(if (showCookie) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showCookie) stringResource(Res.string.hide_label) else stringResource(Res.string.show_label))
                    }
                },
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
                maxLines = 3,
                supportingText = {
                    Text(stringResource(Res.string.characters, cookieInput.length), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )

            AnimatedVisibility(visible = cookieWarnings.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.warnings_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        cookieWarnings.take(3).forEach { warning ->
                            Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        if (cookieWarnings.size > 3) {
                            Text("...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Button(
                onClick = onLogin,
                enabled = cookieInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp).pointerHoverIcon(PointerIcon.Hand),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.login_title), fontWeight = FontWeight.SemiBold)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(Res.string.privacy_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(circleAwareShape())
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}


@Composable
private fun LoadingSection() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                stringResource(Res.string.verifying_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun ErrorSection(message: String, onRetry: () -> Unit, onReset: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    stringResource(Res.string.could_not_load_account),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.back_to_login))
                    }
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.retry))
                    }
                }
            }
        }
    }
}


@Composable
private fun LoggedInSection(
    state: AccountState.LoggedIn,
    onRefresh: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Header de perfil
        AccountProfileHeader(accountInfo = state.accountInfo)

        Spacer(Modifier.height(8.dp))

        // Sección de playlists
        PlaylistsSection(
            playlists = state.playlists,
            isLoading = state.isLoadingPlaylists,
            onRefresh = onRefresh,
            onNavigate = onNavigate
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AccountProfileHeader(accountInfo: com.metrolist.innertube.models.AccountInfo) {
    val cookiePath = ""
    val cookieSize = 0L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                if (!accountInfo.thumbnailUrl.isNullOrBlank()) {
                    MelodistImage(
                        url = accountInfo.thumbnailUrl,
                        contentDescription = accountInfo.name,
                        modifier = Modifier.size(64.dp),
                        shape = circleAwareShape(),
                        placeholderType = PlaceholderType.ARTIST,
                        isLowRes = true  // ✅ Baja resolución en avatares de lista
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(circleAwareShape())
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            accountInfo.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        accountInfo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!accountInfo.email.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            accountInfo.email.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!accountInfo.channelHandle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            accountInfo.channelHandle.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }

                // Badge de sesión activa
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(Res.string.connected), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    border = null
                )
            }

            // Barra de info: dónde está guardada la cookie
            if (cookieSize > 0L) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "Cookie: ${cookieSize} bytes • $cookiePath",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


@Composable
private fun PlaylistsSection(
    playlists: List<PlaylistItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onNavigate: (Route) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header de sección
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(Res.string.your_playlists),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.cd_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when {
            isLoading && playlists.isEmpty() -> {
                // Skeleton loaders
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(6) { PlaylistSkeletonItem() }
                }
            }
            playlists.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            stringResource(Res.string.no_playlists_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    playlists.forEach { playlist ->
                        PlaylistAccountItem(
                            playlist = playlist,
                            onClick = { onNavigate(Route.Playlist(playlist.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistAccountItem(
    playlist: PlaylistItem,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isHovered) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        MelodistImage(
            url = playlist.thumbnail,
            contentDescription = playlist.title,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(10.dp),
            placeholderType = PlaceholderType.PLAYLIST,
            isLowRes = true  // ✅ Baja resolución en miniaturas de playlists
        )

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!playlist.author?.name.isNullOrBlank() || playlist.songCountText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        playlist.author?.name?.let { append(it) }
                        if (playlist.author?.name != null && playlist.songCountText != null) append(" • ")
                        playlist.songCountText?.let { append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}


@Composable
private fun PlaylistSkeletonItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.15f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(shimmerColor)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(shimmerColor)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(shimmerColor)
            )
        }
    }
}


@Composable
private fun CookieExpiredSection(
    cookieInput: String,
    cookieWarnings: List<String>,
    onCookieInputChange: (String) -> Unit,
    onRenew: () -> Unit,
    onLogout: () -> Unit
) {
    var showCookie by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.size(84.dp).clip(circleAwareShape()).background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LockClock, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(stringResource(Res.string.session_expired_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(Res.string.session_expired_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Res.string.renew_cookie), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(Res.string.renew_cookie_desc), style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = cookieInput,
                onValueChange = onCookieInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.new_cookie_label)) },
                placeholder = { Text(stringResource(Res.string.cookie_placeholder), style = MaterialTheme.typography.bodySmall) },
                visualTransformation = if (showCookie) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showCookie = !showCookie }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                        Icon(if (showCookie) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
                maxLines = 3,
                supportingText = {
                    Text(stringResource(Res.string.characters, cookieInput.length), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )

            AnimatedVisibility(visible = cookieWarnings.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(Res.string.warnings_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        cookieWarnings.take(3).forEach { warning ->
                            Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Button(
                onClick = onRenew,
                enabled = cookieInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp).pointerHoverIcon(PointerIcon.Hand),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.renew_session), fontWeight = FontWeight.SemiBold)
            }
 
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.logout))
            }
        }
    }
}
