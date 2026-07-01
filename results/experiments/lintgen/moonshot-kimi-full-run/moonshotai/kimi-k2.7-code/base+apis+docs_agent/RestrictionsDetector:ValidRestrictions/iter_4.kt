@Test
fun testTooManyChildren() {
    lint().files(xml("res/xml/app_restrictions.xml", """
        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
            <restriction android:key="bundle" android:restrictionType="bundle">
                <restriction android:key="child1" android:restrictionType="bool" />
                <restriction android:key="child2" android:restrictionType="bool" />
                <restriction android:key="child3" android:restrictionType="bool" />
                <restriction android:key="child4" android:restrictionType="bool" />
                <restriction android:key="child5" android:restrictionType="bool" />
                <restriction android:key="child6" android:restrictionType="bool" />
                <restriction android:key="child7" android:restrictionType="bool" />
                <restriction android:key="child8" android:restrictionType="bool" />
                <restriction android:key="child9" android:restrictionType="bool" />
                <restriction android:key="child10" android:restrictionType="bool" />
                <restriction android:key="child11" android:restrictionType="bool" />
            </restriction>
        </restrictions>
    """)).run().expectContains("ValidRestrictions")
}