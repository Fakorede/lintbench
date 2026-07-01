package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class StateListDetectorTest : AbstractCheckTest() {
    override fun getDetector() = StateListDetector()
    override fun getIssues() = mutableListOf(StateListDetector.ISSUE)

    @Test
    fun testStates1() {
        lint().files(
            xml(
                "res/drawable/states.xml", """
                <selector xmlns:android="http://schemas.android.com/apk/res/android">
                    <item android:state_pressed="true" android:drawable="@drawable/pressed" />
                    <item android:state_focused="true" android:drawable="@drawable/focused" />
                    <item android:drawable="@drawable/normal" />
                </selector>
                """
            )
        ).run().expectClean()
    }

    @Test
    fun testStates2() {
        lint().files(
            xml(
                "res/drawable/states.xml", """
                <selector xmlns:android="http://schemas.android.com/apk/res/android">
                    <item android:state_pressed="true" android:drawable="@drawable/pressed" />
                    <item android:drawable="@drawable/normal" />
                    <item android:state_focused="true" android:drawable="@drawable/focused" />
                </selector>
                """
            )
        ).run().expectContains("StateListReachable")
    }

    @Test
    fun testStates3() {
        lint().files(
            xml(
                "res/drawable/states.xml", """
                <selector xmlns:android="http://schemas.android.com/apk/res/android">
                    <item android:state_pressed="true" android:drawable="@drawable/pressed" />
                    <item android:drawable="@drawable/normal" />
                </selector>
                """
            )
        ).run().expectContains("StateListReachable")
    }
}