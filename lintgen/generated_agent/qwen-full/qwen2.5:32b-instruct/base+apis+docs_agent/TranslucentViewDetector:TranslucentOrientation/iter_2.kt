package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), XmlScanner {

    companion object {
        private val ISSUE = Issue.create(
            id = "TranslucentViewOrientationConflict",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported on apps with `targetSdkVersion` O or greater since there can be another activity visible behind your activity with a conflicting request.
                
                For example, your activity requests landscape and the visible activity behind your translucent activity requests portrait. In this case, the system can only honor one of the requests and currently prefers to honor the request from non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your app if this state is detected.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("activity")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdkVersion = context.getManifest().targetSdkVersion?.apiLevel ?: 0
        if (targetSdkVersion >= 26) { // Oreo is API level 26
            val screenOrientationAttr = element.getAttributeNS(ANDROID_URI, "screenOrientation")
            val themeAttr = element.getAttributeNS(ANDROID_URI, "theme")

            if (!screenOrientationAttr.isNullOrEmpty() && !themeAttr.isNullOrEmpty()) {
                context.getAndroidContext().resources?.getTheme(themeAttr)?.let { theme ->
                    if (isTranslucentTheme(context, theme)) {
                        context.report(
                            ISSUE,
                            context.getLocation(element),
                            "Mixing screenOrientation and translucent theme is not supported on API level 26 or greater"
                        )
                    }
                }
            }
        }
    }

    private fun isTranslucentTheme(context: XmlContext, themeName: String): Boolean {
        val theme = context.getAndroidContext().resources?.getTheme(themeName)
        return theme != null && theme.isTranslucent
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MANIFEST
    }
}