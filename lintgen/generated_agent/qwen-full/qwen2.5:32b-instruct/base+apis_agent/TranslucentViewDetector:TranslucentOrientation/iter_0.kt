package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.util.*

class TranslucentViewDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "TranslucentViewOrientationConflict",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported on apps with `targetSdkVersion` O or greater since there can be another activity visible behind your activity with a conflicting request.
                
                For example, your activity requests landscape and the visible activity behind your translucent activity requests portrait. In this case, the system can only honor one of the requests and currently prefers to honor the request from non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your app if this state is detected.
            """,
            category = Category.USABILITY,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("activity", "application")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdkVersion = context.getManifest().targetSdkVersion

        if (targetSdkVersion >= AndroidVersionCodes.O) {
            val screenOrientationAttr = element.getAttributeNode("android:screenOrientation")
            val themeAttr = element.getAttributeNode("android:theme")

            if (screenOrientationAttr != null && themeAttr != null) {
                val themeName = themeAttr.value
                val isTranslucentTheme = context.getThemes().any { it.name == themeName && it.isTranslucent }

                if (isTranslucentTheme) {
                    val screenOrientationValue = screenOrientationAttr.value

                    if (screenOrientationValue != "unspecified" && screenOrientationValue != "sensor") {
                        context.report(
                            ISSUE,
                            element,
                            context.getLocation(screenOrientationAttr),
                            "Mixing fixed screen orientation with a translucent theme is not supported on API level ${targetSdkVersion} or greater."
                        )
                    }
                }
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MANIFEST
    }
}