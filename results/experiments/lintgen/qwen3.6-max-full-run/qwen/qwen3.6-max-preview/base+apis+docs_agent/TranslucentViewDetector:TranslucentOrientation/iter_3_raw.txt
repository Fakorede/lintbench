package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported on apps with targetSdkVersion O or greater since there can be another activity visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind your translucent activity requests portrait. In this case the system can only honor one of the requests and currently prefers to honor the request from non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your app if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST_SCOPE)
            )
        )

        private val FIXED_ORIENTATIONS = setOf(
            "portrait", "landscape", "reversePortrait", "reverseLandscape",
            "locked", "nosensor", "userPortrait", "userLandscape"
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion ?: return
        if (targetSdk.apiLevel < 26) return

        val orientationAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SCREEN_ORIENTATION)
        if (orientationAttr == null) return

        val orientationValue = orientationAttr.value
        if (orientationValue !in FIXED_ORIENTATIONS) return

        if (!isTranslucentTheme(context, element)) return

        context.report(
            ISSUE,
            context.getLocation(orientationAttr),
            "Translucent activities should not specify a fixed screen orientation when targeting API 26+"
        )
    }

    private fun isTranslucentTheme(context: XmlContext, activityElement: Element): Boolean {
        var themeAttr = activityElement.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
        if (themeAttr == null) {
            val manifest = activityElement.ownerDocument?.documentElement
            val application = manifest?.getElementsByTagName(SdkConstants.TAG_APPLICATION)?.item(0) as? Element
            if (application != null) {
                themeAttr = application.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
            }
        }

        val themeValue = themeAttr?.value ?: return false
        return themeValue.contains("Translucent", ignoreCase = true) ||
               themeValue.contains("Floating", ignoreCase = true) ||
               themeValue.contains("SwipeToDismiss", ignoreCase = true) ||
               themeValue.contains("Dialog", ignoreCase = true)
    }
}