package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ScreenOrientation
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.NodeList

private const val ANDROID_O_API_LEVEL = 26

class TranslucentViewDetector : Detector(), XmlScanner {

    override fun appliesTo(context: XmlContext): Boolean =
        context.resourceFolderType == ResourceFolderType.MANIFEST

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_ACTIVITY)

    override fun visitElement(context: XmlContext, element: Element) {
        if (!targetsOOrHigher(context)) {
            return
        }

        if (!hasFixedOrientation(element)) {
            return
        }

        if (!hasTranslucentTheme(context, element)) {
            return
        }

        val orientationAttr = element.getAttributeNodeNS(
            SdkConstants.ANDROID_URI,
            SdkConstants.ATTR_SCREEN_ORIENTATION
        ) ?: return

        context.report(
            ISSUE,
            context.getLocation(orientationAttr),
            "Fixed screenOrientation is not supported with a translucent theme when targetSdkVersion is $ANDROID_O_API_LEVEL or higher"
        )
    }

    private fun targetsOOrHigher(context: XmlContext): Boolean {
        val manifestTargetSdk = getManifestTargetSdk(context)
        if (manifestTargetSdk != null) {
            return manifestTargetSdk >= ANDROID_O_API_LEVEL
        }
        return context.project.targetSdk >= ANDROID_O_API_LEVEL
    }

    private fun getManifestTargetSdk(context: XmlContext): Int? {
        val root = context.document.documentElement
        val usesSdk = root.getElementsByTagName(SdkConstants.TAG_USES_SDK).toElementList().firstOrNull()
            ?: return null
        return usesSdk.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TARGET_SDK_VERSION)
            .takeIf { it.isNotBlank() }
            ?.toIntOrNull()
    }

    private fun hasFixedOrientation(element: Element): Boolean {
        val value = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SCREEN_ORIENTATION)
            .takeIf { it.isNotBlank() }
            ?: return false

        val orientation = ScreenOrientation.getByName(value) ?: return true
        return orientation !in NON_FIXED_ORIENTATIONS
    }

    private fun hasTranslucentTheme(context: XmlContext, activity: Element): Boolean {
        val theme = getThemeValue(context, activity) ?: return false
        return theme.contains(TRANSLUCENT_INDICATOR, ignoreCase = true)
    }

    private fun getThemeValue(context: XmlContext, activity: Element): String? {
        val activityTheme = activity.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
            .takeIf { it.isNotBlank() }
        if (activityTheme != null) {
            return activityTheme
        }

        val root = context.document.documentElement
        val application = root.getElementsByTagName(SdkConstants.TAG_APPLICATION).toElementList().firstOrNull()
            ?: return null
        return application.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
            .takeIf { it.isNotBlank() }
    }

    private fun NodeList.toElementList(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    companion object {
        private val NON_FIXED_ORIENTATIONS = setOf(
            ScreenOrientation.UNSPECIFIED,
            ScreenOrientation.BEHIND
        )

        private const val TRANSLUCENT_INDICATOR = "Translucent"

        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported
                on apps with targetSdkVersion O or greater, since there can be another activity
                visible behind your activity with a conflicting request. On devices running
                platform version O or greater this will throw an exception.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}