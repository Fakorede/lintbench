class TranslucentViewDetectorTest : AbstractCheckTest() {
    override fun getDetector() = TranslucentViewDetector()
    override fun getIssues() = listOf(TranslucentViewDetector.ISSUE)
    override fun getTypes() = setOf(TestResourceFile.Type.XML) // maybe

    fun testThemeFromActivity() {
        lint().files(
            xml("AndroidManifest.xml", """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="test.pkg">
                    <uses-sdk android:targetSdkVersion="26" />
                    <application android:theme="@style/AppTheme">
                        <activity android:name=".MainActivity"
                            android:screenOrientation="landscape"
                            android:theme="@style/Theme.Translucent" />
                    </application>
                </manifest>
            """.trimIndent()),
            xml("res/values/styles.xml", """
                <resources>
                    <style name="Theme.Translucent" parent="android:style/Theme">
                        <item name="android:windowIsTranslucent">true</item>
                    </style>
                </resources>
            """.trimIndent())
        ).run().expectContains(TRANSLUCENT_ORIENTATION_ID)
    }
}