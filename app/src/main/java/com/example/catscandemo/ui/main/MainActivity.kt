package com.example.catscandemo.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.catscandemo.presentation.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

// 商务蓝色系：日间
private val CatScanLightColors = lightColorScheme(
    primary = Color(0xFF0F5FA6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8F8),
    onPrimaryContainer = Color(0xFF0A2E4F),
    secondary = Color(0xFF3F6B8F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E5F1),
    onSecondaryContainer = Color(0xFF10283A),
    tertiary = Color(0xFF4A7CA6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8E9F6),
    onTertiaryContainer = Color(0xFF0E2C42),
    background = Color(0xFFFAFCFE),
    onBackground = Color(0xFF171C21),
    surface = Color(0xFFFAFCFE),
    onSurface = Color(0xFF171C21),
    surfaceVariant = Color(0xFFDDE5EC),
    onSurfaceVariant = Color(0xFF41484F),
    outline = Color(0xFF71787F),
    outlineVariant = Color(0xFFC1C9D0)
)

// 商务蓝色系：夜间
private val CatScanDarkColors = darkColorScheme(
    primary = Color(0xFF9CC8EE),
    onPrimary = Color(0xFF0A2E4F),
    primaryContainer = Color(0xFF1C4E7C),
    onPrimaryContainer = Color(0xFFD7E8F8),
    secondary = Color(0xFFA5C7DE),
    onSecondary = Color(0xFF10283A),
    secondaryContainer = Color(0xFF2A4A62),
    onSecondaryContainer = Color(0xFFD5E5F1),
    tertiary = Color(0xFF9CC0DC),
    onTertiary = Color(0xFF0E2C42),
    tertiaryContainer = Color(0xFF2E4C66),
    onTertiaryContainer = Color(0xFFD8E9F6),
    background = Color(0xFF10151A),
    onBackground = Color(0xFFDFE4EA),
    surface = Color(0xFF10151A),
    onSurface = Color(0xFFDFE4EA),
    surfaceVariant = Color(0xFF3A434C),
    onSurfaceVariant = Color(0xFFBFC7CF),
    outline = Color(0xFF8A929B),
    outlineVariant = Color(0xFF41484F)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val darkTheme = when (viewModel.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(
                colorScheme = if (darkTheme) CatScanDarkColors else CatScanLightColors
            ) {
                Surface {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
