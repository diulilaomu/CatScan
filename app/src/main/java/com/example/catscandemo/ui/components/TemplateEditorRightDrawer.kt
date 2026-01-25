package com.example.catscandemo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.catscandemo.ui.main.MainViewModel

@Composable
fun TemplateEditorRightDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    widthFraction: Float = 0.9f
) {
    if (visible) {
        BackHandler { onDismiss() }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val drawerWidth = (screenWidthDp * widthFraction).dp

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onDismiss)
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(drawerWidth)
                    .windowInsetsPadding(WindowInsets.safeDrawing), // 避开状态栏/刘海/手势栏
                tonalElevation = 6.dp,
                shape = MaterialTheme.shapes.large
            ) {
                TemplateEditorNavigator(
                    viewModel = viewModel,
                    onClose = onDismiss,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
