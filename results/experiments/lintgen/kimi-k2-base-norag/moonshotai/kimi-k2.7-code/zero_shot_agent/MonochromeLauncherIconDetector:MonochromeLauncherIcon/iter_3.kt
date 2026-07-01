class MonochromeLauncherIconDetectorTest : AbstractCheckTest() {
    override fun getDetector(): Detector = MonochromeLauncherIconDetector()

    fun testDocumentationExample() {
        val expected = """
            res/mipmap-anydpi-v26/ic_launcher.xml:2: Warning: Monochrome icon is not defined [MonochromeLauncherIcon]
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            The system may use the coloring ...
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        """.trimIndent()
        lint().files(
            xml("res/mipmap-anydpi-v26/ic_launcher.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@drawable/ic_launcher_background"/>
                    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
                </adaptive-icon>
            """.trimIndent())
        ).run().expect(expected)
    }

    fun testOnlyRoundIconMonochrome() {
        lint().files(
            xml("res/mipmap-anydpi-v26/ic_launcher.xml", """
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@drawable/ic_launcher_background"/>
                    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
                </adaptive-icon>
            """.trimIndent()),
            xml("res/mipmap-anydpi-v26/ic_launcher_round.xml", """
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@drawable/ic_launcher_background"/>
                    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
                    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
                </adaptive-icon>
            """.trimIndent())
        ).run().expectContains("MonochromeLauncherIcon")
    }
}