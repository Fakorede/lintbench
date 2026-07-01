package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.client.api.ResourceResolver
import com.android.tools.lint.client.api.ResourceValue
import com.android.tools.lint.client.api.StyleResourceValue
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), Detector.XmlScanner {

    companion object {
        private val FIXED_ORIENTATIONS = setOf(
            "portrait", "landscape",
            "sensorPortrait", "sensorLandscape",
            "reversePortrait", "reverseLandscape",
            "userPortrait", "userLandscape"
        )

        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind \
                your translucent activity request portrait. In this case the system can only \
                honor one of the requests and currently prefers to honor the request from \
                non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_AND_RESOURCE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_ACTIVITY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.mainProject.targetSdkVersion ?: context.mainProject.targetSdk ?: 0
        if (targetSdk < 26) return

        val orientationAttr = element.getAttributeNode(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SCREEN_ORIENTATION) ?: return
        val orientation = orientationAttr.value
        if (orientation !in FIXED_ORIENTATIONS) return

        val theme = getTheme(context, element)
        if (!isTranslucentTheme(context, theme)) return

        context.report(
            ISSUE,
            orientationAttr,
            context.getLocation(orientationAttr),
            "Fixed screen orientation (`$orientation`) is not supported with translucent themes on API 26+ and will cause a crash"
        )
    }

    private fun getTheme(context: XmlContext, activity: Element): String? {
        var theme = activity.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
        if (theme.isNullOrEmpty()) {
            val manifest = activity.ownerDocument.documentElement
            val app = manifest.getElementsByTagName(SdkConstants.TAG_APPLICATION).item(0) as? Element
            theme = app?.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
        }
        return theme?.takeIf { it.isNotEmpty() }
    }

    private fun isTranslucentTheme(context: XmlContext, themeName: String?): Boolean {
        if (themeName == null) return false

        val resolver = context.resourceResolver
        val style = resolver.resolveStyle(themeName)

        if (style != null) {
            if (isAttributeTrue(resolver, style, "windowIsTranslucent")) return true
            if (isAttributeTrue(resolver, style, "windowIsFloating")) return true
            if (isAttributeTrue(resolver, style, "windowShowWallpaper")) return true
        }

        return themeName.contains("Translucent", ignoreCase = true) ||
               themeName.contains("Dialog", ignoreCase = true) ||
               themeName.contains("Floating", ignoreCase = true)
    }

    private fun isAttributeTrue(resolver: ResourceResolver, style: StyleResourceValue?, attrName: String): Boolean {
        val value: ResourceValue? = resolver.findItemInStyle(style, attrName, true)
        return value?.value?.toBoolean() == true
    }
}