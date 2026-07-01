package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class DuplicateIdDetectorTest {
    @Test
    fun testBasic() {
        lint().files(
            xml("res/layout/layout1.xml", """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/foo">
                    <Button android:id="@+id/foo"/>
                </LinearLayout>
            """),
            xml("res/layout/layout2.xml", """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/foo"/>
            """)
        ).run().expectClean()
    }

    @Test
    fun testDuplicate() {
        lint().files(
            xml("res/layout/layout1.xml", """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/foo">
                    <include layout="@layout/layout2"/>
                </LinearLayout>
            """),
            xml("res/layout/layout2.xml", """
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:id="@+id/foo"/>
            """)
        ).run().expect("""
            res/layout/layout1.xml: Error: ... [DuplicateIncludedIds]
            1 errors, 0 warnings
        """)
    }

    @Test
    fun testDuplicateChains() { ... }

    @Test
    fun testSuppress() { ... }
}