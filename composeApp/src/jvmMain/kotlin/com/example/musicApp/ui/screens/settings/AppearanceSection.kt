package com.example.musicApp.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.expressive.SettingsSwitch
import com.example.musicApp.data.repository.*
import com.example.musicApp.ui.screens.shared.displayName
import com.example.musicApp.viewmodels.SettingsViewModel
import lyrik.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceSettingsGroup(
    viewModel: SettingsViewModel,
    colors: ListItemColors
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val darkLevel by viewModel.darkLevel.collectAsState()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val islandStyle by viewModel.islandStyle.collectAsState()
    val themePalette by viewModel.themePalette.collectAsState()
    val dynamicColor by viewModel.dynamicColorFromArtwork.collectAsState()
    val navigationRailStyle by viewModel.navigationRailStyle.collectAsState()

    var showThemeDropdown by remember { mutableStateOf(false) }
    var showDarkLevelDropdown by remember { mutableStateOf(false) }
    var showLayoutDropdown by remember { mutableStateOf(false) }
    var showIslandStyleDropdown by remember { mutableStateOf(false) }
    var showPaletteDropdown by remember { mutableStateOf(false) }
    var showNavigationRailStyle by remember { mutableStateOf(false) }

    SettingsGroup(
        title = { Text(stringResource(Res.string.section_appearance)) },
        colors = colors,
    ) {
        DropdownSelector(
            label = stringResource(Res.string.theme),
            icon = Icons.Rounded.DarkMode,
            currentValue = themeMode.displayName(),
            segmentedShape = ListItemDefaults.segmentedShapes(index = 0, count = 6),
            expanded = showThemeDropdown,
            onExpandedChange = { showThemeDropdown = it },
            options = ThemeMode.entries.map { it to it.displayName() },
            isSelected = { it == themeMode },
            onSelect = { viewModel.setThemeMode(it); showThemeDropdown = false },
            colors = colors
        )
        DropdownSelector(
            label = stringResource(Res.string.settings_dark_level),
            icon = Icons.Rounded.Contrast,
            currentValue = darkLevel.displayName(),
            expanded = showDarkLevelDropdown,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 1, count = 6),
            onExpandedChange = { showDarkLevelDropdown = it },
            options = DarkLevel.entries.map { it to it.displayName() },
            isSelected = { it == darkLevel },
            onSelect = { viewModel.setDarkLevel(it); showDarkLevelDropdown = false },
            colors = colors,
        )
        DropdownSelector(
            label = stringResource(Res.string.settings_layout),
            icon = Icons.Rounded.Dashboard,
            currentValue = layoutMode.displayName(),
            expanded = showLayoutDropdown,
            segmentedShape = ListItemDefaults.segmentedShapes(index = 2, count = 6),
            onExpandedChange = { showLayoutDropdown = it },
            options = LayoutMode.entries.map { it to it.displayName() },
            isSelected = { it == layoutMode },
            onSelect = { viewModel.setLayoutMode(it); showLayoutDropdown = false },
            colors = colors,
        )
        if (layoutMode == LayoutMode.ISLANDS) {
            DropdownSelector(
                label = stringResource(Res.string.settings_island_style),
                icon = Icons.Rounded.ViewAgenda,
                currentValue = islandStyle.displayName(),
                expanded = showIslandStyleDropdown,
                onExpandedChange = { showIslandStyleDropdown = it },
                options = IslandStyle.entries.map { it to it.displayName() },
                isSelected = { it == islandStyle },
                onSelect = { viewModel.setIslandStyle(it); showIslandStyleDropdown = false },
                colors = colors,
            )
        }
        DropdownSelector(
            label = stringResource(Res.string.navigation_rail_style),
            icon = Icons.Rounded.Menu,
            currentValue = navigationRailStyle.displayName(),
            expanded = showNavigationRailStyle,
            onExpandedChange = { showNavigationRailStyle = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 3, count = 6),
            options = NavigationRailStyle.entries.map { it to it.displayName() },
            isSelected = { it == navigationRailStyle },
            onSelect = { viewModel.setNavigationRailStyle(it); showNavigationRailStyle = false },
            colors = colors,
        )
        DropdownSelector(
            label = stringResource(Res.string.color_palette),
            icon = Icons.Rounded.Palette,
            currentValue = themePalette.displayName(),
            expanded = showPaletteDropdown,
            onExpandedChange = { showPaletteDropdown = it },
            segmentedShape = ListItemDefaults.segmentedShapes(index = 4, count = 6),
            options = ThemePalette.entries.map { it to it.displayName() },
            isSelected = { it == themePalette },
            onSelect = { viewModel.setThemePalette(it); showPaletteDropdown = false },
            colors = colors,
            paletteItem = true,
        )
        SettingsSwitch(
            icon = { Icon(Icons.Rounded.ColorLens, null) },
            title = { Text(stringResource(Res.string.dynamic_colors)) },
            shapes = ListItemDefaults.segmentedShapes(index = 5, count = 6),
            colors = colors,
            state = dynamicColor,
            onCheckedChange = { viewModel.setDynamicColorFromArtwork(it) }
        )
    }
}
