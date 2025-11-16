package com.example.catscandemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.catscandemo.ui.main.MainScreen
import com.example.catscandemo.ui.main.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 注：这里没有使用依赖注入框架，手动创建 ViewModel。
        val viewModel = MainViewModel()

        setContent {
            MainScreen(viewModel = viewModel)
        }
    }
}
