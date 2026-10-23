package com.example.catscandemo

import com.example.catscandemo.data.network.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本比较单测：GitHub release tag（v4.2.1）对本地版本名（4.2.0），
 * 只有严格更新才提示，相等/更低/无法解析一律不提示。
 */
class UpdateCheckerTest {

    @Test
    fun `候选大于当前才算更新`() {
        assertTrue(UpdateChecker.isNewerVersion("v4.2.1", "4.2.0"))
    }

    @Test
    fun `忽略v前缀的相等版本不提示`() {
        assertFalse(UpdateChecker.isNewerVersion("v4.2.0", "4.2.0"))
    }

    @Test
    fun `分段按数值而非字典序比较`() {
        assertTrue(UpdateChecker.isNewerVersion("v4.10.0", "v4.9.9"))
    }

    @Test
    fun `候选更低不提示`() {
        assertFalse(UpdateChecker.isNewerVersion("v4.1.9", "4.2.0"))
    }

    @Test
    fun `短版本缺段补零比较`() {
        assertFalse(UpdateChecker.isNewerVersion("v4.2", "4.2.0"))
        assertTrue(UpdateChecker.isNewerVersion("v4.3", "4.2.0"))
    }

    @Test
    fun `多出的小版本段算更新`() {
        assertTrue(UpdateChecker.isNewerVersion("v4.2.0.1", "4.2.0"))
    }

    @Test
    fun `预发布后缀不视为更新`() {
        assertFalse(UpdateChecker.isNewerVersion("4.2.0-beta", "4.2.0"))
    }

    @Test
    fun `无法解析的tag不提示`() {
        assertFalse(UpdateChecker.isNewerVersion("", "4.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("beta", "4.2.0"))
    }
}
