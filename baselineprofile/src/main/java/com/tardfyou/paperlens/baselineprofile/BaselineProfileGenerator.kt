package com.tardfyou.paperlens.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 5_000)

            device.findObject(By.desc("文档"))?.click()
            device.waitForIdle()
            device.findObject(By.desc("重点"))?.click()
            device.waitForIdle()
            device.findObject(By.desc("设置"))?.click()
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.tardfyou.paperlens"
    }
}
