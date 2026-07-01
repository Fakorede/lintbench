package com.android.tools.lint.checks

import ...

class StateListDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = StateListDetector()
    override fun getIssues(): List<Issue> = listOf(StateListDetector.ISSUE)

    @Test
    fun testStates1() { ... } // ~line 30
    @Test
    fun testStates2() { ... } // ~line 70
    @Test
    fun testStates3() { ... } // ~line 110
}