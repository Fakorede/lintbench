package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
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

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE),
                EnumSet.of(Scope.MANIFEST),
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )

        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"

        private val FIXED_ORIENTATIONS = setOf(
            "landscape",
            "portrait",
            "reverseLandscape",
            "reversePortrait",
            "sensorLandscape",
            "sensorPortrait",
            "userLandscape",
            "userPortrait",
            "locked"
        )

        private val TRANSLUCENT_THEME_PATTERNS = listOf(
            "Translucent",
            "translucent",
            "Dialog",
            "Floating",
            "floating"
        )

        private val TRANSLUCENT_STYLE_ATTRIBUTES = setOf(
            "windowIsTranslucent",
            "android:windowIsTranslucent",
            "windowSwipeToDismiss",
            "android:windowSwipeToDismiss"
        )
    }

    private val styleParentMap = mutableMapOf<String, String?>()
    private val styleAttributeMap = mutableMapOf<String, MutableMap<String, String>>()
    private val translucentStyleCache = mutableMapOf<String, Boolean>()

    private var applicationTheme: String? = null

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, TAG_APPLICATION, "style")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "style" -> collectStyleInfo(element)
            TAG_APPLICATION -> {
                val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                if (theme.isNotEmpty()) {
                    applicationTheme = theme
                }
            }
            TAG_ACTIVITY -> visitActivity(context, element)
        }
    }

    private fun collectStyleInfo(element: Element) {
        val name = element.getAttribute(ATTR_NAME)
        if (name.isEmpty()) return

        val parent = element.getAttribute("parent").takeIf { it.isNotEmpty() }
        styleParentMap[name] = parent

        val attrs = styleAttributeMap.getOrPut(name) { mutableMapOf() }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == "item") {
                val itemName = child.getAttribute(ATTR_NAME)
                val itemValue = child.textContent?.trim() ?: ""
                if (itemName.isNotEmpty()) {
                    attrs[itemName] = itemValue
                }
            }
        }
    }

    private fun visitActivity(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdk
        if (targetSdk < 26) return

        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        if (orientation.isEmpty() || !FIXED_ORIENTATIONS.contains(orientation)) return

        val activityTheme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
        val effectiveTheme = when {
            activityTheme.isNotEmpty() -> activityTheme
            applicationTheme != null -> applicationTheme!!
            else -> return
        }

        if (isTranslucentTheme(effectiveTheme)) {
            val location = context.getElementLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "Should not specify a fixed `screenOrientation` with a translucent theme: " +
                    "the theme specifies a translucent background and the orientation request " +
                    "can conflict with an orientation request from behind the translucent activity"
            )
        }
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        val normalized = normalizeThemeName(theme)
        return translucentStyleCache.getOrPut(normalized) {
            isTranslucentThemeInternal(normalized, mutableSetOf())
        }
    }

    private fun isTranslucentThemeInternal(themeName: String, visited: MutableSet<String>): Boolean {
        if (!visited.add(themeName)) return false

        for (pattern in TRANSLUCENT_THEME_PATTERNS) {
            if (themeName.contains(pattern)) return true
        }

        val attrs = styleAttributeMap[themeName]
        if (attrs != null) {
            for (translucentAttr in TRANSLUCENT_STYLE_ATTRIBUTES) {
                if (attrs[translucentAttr] == "true") return true
            }
        }

        val parent = styleParentMap[themeName]
        if (parent != null) {
            val normalizedParent = normalizeThemeName(parent)
            if (isTranslucentThemeInternal(normalizedParent, visited)) return true
        }

        val dotIndex = themeName.lastIndexOf('.')
        if (dotIndex > 0) {
            val implicitParent = themeName.substring(0, dotIndex)
            if (isTranslucentThemeInternal(implicitParent, visited)) return true
        }

        return false
    }

    private fun normalizeThemeName(theme: String): String {
        return theme
            .removePrefix("@style/")
            .removePrefix("@android:style/")
            .removePrefix("@*android:style/")
            .removePrefix("?attr/")
            .removePrefix("?android:attr/")
            .trim()
    }
}