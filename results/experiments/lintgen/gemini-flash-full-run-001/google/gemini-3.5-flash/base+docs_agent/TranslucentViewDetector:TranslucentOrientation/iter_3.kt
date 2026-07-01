package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, "activity-alias")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = getTargetSdkVersion(context)
        if (targetSdk < 26) {
            return
        }

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            ?: element.getAttributeNode("android:$ATTR_SCREEN_ORIENTATION")
            ?: return

        val orientation = orientationAttr.value
        if (!isFixedOrientation(orientation)) {
            return
        }

        var theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
        if (theme.isEmpty()) {
            theme = element.getAttribute("android:$ATTR_THEME")
        }
        if (theme.isEmpty()) {
            val parent = element.parentNode as? Element
            if (parent != null && (parent.tagName == TAG_APPLICATION || parent.nodeName == "application")) {
                theme = parent.getAttributeNS(ANDROID_URI, ATTR_THEME)
                if (theme.isEmpty()) {
                    theme = parent.getAttribute("android:$ATTR_THEME")
                }
            }
        }

        if (theme.isNotEmpty() && isTranslucentTheme(context, theme)) {
            context.report(
                ISSUE,
                orientationAttr,
                context.getLocation(orientationAttr),
                "Should not specify screen orientation with a translucent theme when targeting O or higher"
            )
        }
    }

    private fun getTargetSdkVersion(context: XmlContext): Int {
        val projectTarget = context.project.targetSdkVersion.apiLevel
        if (projectTarget >= 26) {
            return projectTarget
        }

        val root = context.document.documentElement
        if (root != null) {
            val usesSdkList = root.getElementsByTagName("uses-sdk")
            if (usesSdkList.length > 0) {
                val usesSdk = usesSdkList.item(0) as Element
                val targetSdkStr = usesSdk.getAttributeNS(ANDROID_URI, "targetSdkVersion")
                    .ifEmpty { usesSdk.getAttribute("android:targetSdkVersion") }
                if (targetSdkStr.isNotEmpty()) {
                    try {
                        return targetSdkStr.toInt()
                    } catch (e: NumberFormatException) {
                        val firstChar = targetSdkStr.firstOrNull()
                        if (firstChar != null && firstChar.isLetter()) {
                            if (firstChar.uppercaseChar() >= 'O') {
                                return 26
                            }
                        }
                    }
                }
            }
        }
        return projectTarget
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return when (orientation) {
            "portrait", "reversePortrait", "sensorPortrait", "userPortrait",
            "landscape", "reverseLandscape", "sensorLandscape", "userLandscape",
            "locked" -> true
            else -> false
        }
    }

    private fun isTranslucentTheme(context: XmlContext, theme: String): Boolean {
        try {
            if (context.client.isTranslucentTheme(context.project, theme)) {
                return true
            }
        } catch (e: Throwable) {
            // Fallback to string matching if resolution fails
        }
        val lower = theme.lowercase()
        return lower.contains("translucent") ||
                lower.contains("dialog") ||
                lower.contains("floating") ||
                lower.contains("nodisplay") ||
                lower.contains("transparent") ||
                lower.contains("panel")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.
                
                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            )
        )
    }
}