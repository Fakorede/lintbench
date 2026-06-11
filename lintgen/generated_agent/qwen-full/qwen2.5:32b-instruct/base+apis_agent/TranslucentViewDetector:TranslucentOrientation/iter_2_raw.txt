package com.android.tools.lint.checks

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.android.utils.XmlUtils.getAttributeValue
import com.android.utils.parseTargetSdkVersion
import com.android.utils.readThemes
import com.android.utils.toPsiFile
import com.android.utils.xml.asSequence
import com.android.utils.xml.getAttributeNode
import com.android.utils.xml.getValue
import com.android.utils.xml.isAttrDataValue
import com.android.utils.xml.isThemeAttributeValue
import com.android.utils.xml.readAttribute
import com.android.utils.xml.readAttributes
import com.android.utils.xml.readThemes
import com.android.utils.xml.toPsiFile
import com.android.utils.xml.asSequence
import com.android.utils.xml.getAttributeNode
import com.android.utils.xml.getValue
import com.android.utils.xml.isAttrDataValue
import com.android.utils.xml.isThemeAttributeValue
import com.android.utils.xml.readAttribute
import com.android.utils.xml.readAttributes
import com.android.utils.xml.readThemes
import com.android.utils.xml.toPsiFile
import com.android.utils.xml.asSequence
import com.android.utils.xml.getAttributeNode
import com.android.utils.xml.getValue
import com.android.utils.xml.isAttrDataValue
import com.android.utils.xml.isThemeAttributeValue
import org.w3c.dom.Attr
import org.w3c.dom.Element

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
        val manifestFile = context.file.toPsiFile() ?: return
        val targetSdkVersion = parseTargetSdkVersion(manifestFile)

        if (targetSdkVersion >= 26) { // Android Oreo API level is 26
            val screenOrientationAttr = element.getAttributeNode(ATTR_SCREEN_ORIENTATION)
            val themeAttr = element.getAttributeNode(ATTR_THEME)

            if (screenOrientationAttr != null && themeAttr != null) {
                val themeName = getAttributeValue(themeAttr, context.project)
                val themes = readThemes(context.project)
                val isTranslucentTheme = themes.any { it.name == themeName && it.isTranslucent }

                if (isTranslucentTheme) {
                    val screenOrientationValue = getAttributeValue(screenOrientationAttr, context.project)

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

    @VisibleForTesting
    internal fun getAttributeValue(attr: Attr, project: Project): String? {
        val value = attr.getValue()
        if (value.isAttrDataValue()) {
            return XmlUtils.getAttrDataValue(value, project)
        }
        return value
    }
}