package com.example.catscandemo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.catscandemo.domain.model.MarkMode
import com.example.catscandemo.domain.model.ScanData
import com.example.catscandemo.domain.model.ScanResult
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.ui.components.ResultItemController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 复现"模板历史记录展开闪退"：按 MainScreen 真实结构（LazyColumn + animateItem +
 * AnimatedVisibility + controller.Render）逐层叠加，定位崩溃层。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResultItemExpandCrashTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleItem() = ScanResult(
        id = 1L,
        index = 0,
        scanData = ScanData(
            text = "A00001",
            floor = "1层",
            room = "101",
            tag = "待检",
            templateId = "t1"
        )
    )

    private fun sampleTemplate() = TemplateModel(
        id = "t1",
        name = "猫猫1栋",
        markMode = MarkMode.ROOM,
        maxFloor = 3,
        roomCountPerFloor = 4,
        tags = listOf("待检", "已检")
    )

    /** 第 1 层：脱离 LazyColumn，直接 Render 后展开 */
    @Test
    fun expandRow_directRender() {
        val controller = ResultItemController(sampleItem(), {}, {}, {})
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    controller.Render(
                        highlight = false,
                        template = sampleTemplate(),
                        lineNumber = 1
                    )
                }
            }
        }
        composeRule.runOnIdle { controller.toggleExpanded() }
        composeRule.waitForIdle()
        Thread.sleep(300)
    }

    /** 第 2 层：完全复刻 MainScreen 的 LazyColumn 行结构后展开 */
    @Test
    fun expandRow_inLazyColumnLikeMainScreen() {
        val items = listOf(sampleItem())
        val holder = arrayOfNulls<ResultItemController>(1)
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    LazyColumn {
                        items(items, key = { it.id }) { item ->
                            val controller = remember(item.id) {
                                ResultItemController(
                                    initialItem = item,
                                    onDelete = {},
                                    onClickCopy = {},
                                    onUpdate = {}
                                ).also { holder[0] = it }
                            }
                            LaunchedEffect(item) { controller.syncItem(item) }
                            Column(
                                modifier = Modifier.animateItem()
                            ) {
                                val enterState = remember {
                                    MutableTransitionState(false).apply { targetState = true }
                                }
                                AnimatedVisibility(
                                    visibleState = enterState,
                                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn()
                                ) {
                                    controller.Render(
                                        highlight = false,
                                        template = sampleTemplate(),
                                        lineNumber = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { holder[0]?.toggleExpanded() }
        composeRule.waitForIdle()
        Thread.sleep(300)
    }
}
