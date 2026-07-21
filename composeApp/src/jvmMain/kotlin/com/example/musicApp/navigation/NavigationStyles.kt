package com.example.musicApp.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.nav_account
import lyrik.composeapp.generated.resources.nav_home
import lyrik.composeapp.generated.resources.nav_library
import lyrik.composeapp.generated.resources.nav_listen_together
import lyrik.composeapp.generated.resources.nav_search
import lyrik.composeapp.generated.resources.nav_settings
import org.jetbrains.compose.resources.stringResource

@Composable
fun NavigationRailDefault(
    activeConfig: ScreenConfig,
    changeQueueVisible: (Boolean) -> Unit,
    rootComponent: RootComponent,
    changeNowPlayingExpanded : (Boolean) -> Unit,

){
    NavigationRail(
        modifier = Modifier.width(90.dp),
        containerColor = Color.Transparent,
    ) {

        Spacer(Modifier.height(26.dp))

            mainTabs.forEach { tab ->
                NavigationRailItem(
                    selected = activeConfig == tab.config,
                    onClick = {
                        changeNowPlayingExpanded(false)
                        rootComponent.switchTab(tab.config)
                    },
                    icon = { Icon(tab.icon, null) },
                    label = {
                        Text(
                            when (tab.config) {
                                ScreenConfig.Home -> stringResource(Res.string.nav_home)
                                ScreenConfig.Search -> stringResource(Res.string.nav_search)
                                ScreenConfig.Library -> stringResource(Res.string.nav_library)
                                else -> ""
                            }
                        )
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    alwaysShowLabel = false,
                )
            }

            Spacer(Modifier.weight(1f))

            bottomTabs.forEach { tab ->
                NavigationRailItem(
                    selected = activeConfig == tab.config,
                    onClick = {
                        changeNowPlayingExpanded(false)
                        changeQueueVisible(false)
                        rootComponent.switchTab(tab.config)
                    },
                    icon = { Icon(tab.icon, null) },
                    label = {
                        Text(
                            when (tab.config) {
                                ScreenConfig.Account -> stringResource(Res.string.nav_account)
                                ScreenConfig.Settings -> stringResource(Res.string.nav_settings)
                                ScreenConfig.ListenTogether -> stringResource(Res.string.nav_listen_together)
                                else -> ""
                            }
                        )
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    alwaysShowLabel = false,
                )
            }
            Spacer(Modifier.height(16.dp))


    }
}

@Composable
fun WideNavigationRail(
    activeConfig: ScreenConfig,
    changeQueueVisible: (Boolean) -> Unit,
    rootComponent: RootComponent,
    changeNowPlayingExpanded: (Boolean) -> Unit,
) {
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val isExpanded = state.targetValue == WideNavigationRailValue.Expanded
    val headerDescription = if (isExpanded) "Collapse rail" else "Expand rail"

    WideNavigationRail(
        state = state,
        colors =  WideNavigationRailDefaults.colors(
            containerColor = Color.Transparent,
        ),
        header = {
            IconButton(
                modifier =
                    Modifier.padding(start = 24.dp).semantics {
                        stateDescription = if (isExpanded) "Expanded" else "Collapsed"
                    },
                onClick = {
                    scope.launch {
                        if (isExpanded) state.collapse() else state.expand()
                    }
                },
            ) {
                if (isExpanded) {
                    Icon(Icons.AutoMirrored.Filled.MenuOpen, headerDescription)
                } else {
                    Icon(Icons.Filled.Menu, headerDescription)
                }
            }
        }
    ) {
            mainTabs.forEach { tab ->
                WideNavigationRailItem(
                    railExpanded = isExpanded,
                    icon = { Icon(tab.icon, null) },
                    label = {
                        Text(
                            when (tab.config) {
                                ScreenConfig.Home -> stringResource(Res.string.nav_home)
                                ScreenConfig.Search -> stringResource(Res.string.nav_search)
                                ScreenConfig.Library -> stringResource(Res.string.nav_library)
                                else -> ""
                            }
                        )
                    },
                    selected = activeConfig == tab.config,
                    onClick = {
                        changeNowPlayingExpanded(false)
                        rootComponent.switchTab(tab.config)
                    },
                )
            }

            bottomTabs.forEach { tab ->
                WideNavigationRailItem(
                    railExpanded = isExpanded,
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = {
                        Text(
                            when (tab.config) {
                                ScreenConfig.Account -> stringResource(Res.string.nav_account)
                                ScreenConfig.Settings -> stringResource(Res.string.nav_settings)
                                ScreenConfig.ListenTogether -> stringResource(Res.string.nav_listen_together)
                                else -> ""
                            }
                        )
                    },
                    selected = activeConfig == tab.config,
                    onClick = {
                        changeNowPlayingExpanded(false)
                        changeQueueVisible(false)
                        rootComponent.switchTab(tab.config)
                    },
                )
        }
    }
}