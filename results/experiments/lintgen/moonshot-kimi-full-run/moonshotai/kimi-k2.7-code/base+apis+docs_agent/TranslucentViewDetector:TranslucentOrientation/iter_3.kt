@Test
fun testThemeFromActivity() {
    lint().files(
        xml("AndroidManifest.xml", """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="test.pkg">
                <uses-sdk android:targetSdkVersion="26"/>
                <application>
                    <activity android:name=".MainActivity"
                        android:theme="@style/Theme.Translucent"
                        android:screenOrientation="landscape"/>
                </application>
            </manifest>
        """.trimIndent()),
        xml("res/values/styles.xml", """
            <resources>
                <style name="Theme.Translucent" parent="android:Theme">
                    <item name="android:windowIsTranslucent">true</item>
                </style>
            </resources>
        """.trimIndent())
    ).run().expectContains("TranslucentOrientation")
}