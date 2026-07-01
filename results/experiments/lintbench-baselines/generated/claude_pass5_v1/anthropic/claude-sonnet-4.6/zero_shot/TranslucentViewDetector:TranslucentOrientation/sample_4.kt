package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFixer
import com.android.tools.lint.detector.api.Location
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
                on apps with `targetSdkVersion` O or greater since there can be an another \
                activity visible behind your activity with a conflicting request.

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
                EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
        )

        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"
        private const val ATTR_WINDOW_IS_TRANSLUCENT = "windowIsTranslucent"
        private const val ATTR_WINDOW_IS_FLOATING = "windowIsFloating"

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

        private const val MIN_SDK_WITH_EXCEPTION = 26 // Android O
    }

    // Map from theme name to whether it is translucent
    private val themeTranslucencyCache = mutableMapOf<String, Boolean?>()

    // Map from theme name to its parent theme name
    private val themeParentMap = mutableMapOf<String, String?>()

    // Map from theme name to attributes defined in that theme
    private val themeAttributeMap = mutableMapOf<String, MutableMap<String, String>>()

    // Activities that have a fixed orientation
    private data class ActivityInfo(
        val element: Element,
        val orientation: String,
        val themeName: String?,
        val location: Location
    )

    private val activitiesWithOrientation = mutableListOf<ActivityInfo>()
    private var applicationTheme: String? = null
    private var targetSdkVersion: Int = 1

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, TAG_APPLICATION, "style", "item")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "style" -> processStyleElement(context, element)
            TAG_APPLICATION -> processApplicationElement(context, element)
            TAG_ACTIVITY -> processActivityElement(context, element)
        }
    }

    private fun processStyleElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            .takeIf { it.isNotEmpty() }
            ?: element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() }
            ?: return

        val parent = element.getAttributeNS(ANDROID_URI, "parent")
            .takeIf { it.isNotEmpty() }
            ?: element.getAttribute("parent").takeIf { it.isNotEmpty() }
            ?: inferParentFromName(name)

        themeParentMap[name] = parent

        val attrs = themeAttributeMap.getOrPut(name) { mutableMapOf() }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == "item") {
                val itemName = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    .takeIf { it.isNotEmpty() }
                    ?: child.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() }
                    ?: continue
                val value = child.textContent?.trim() ?: continue
                attrs[itemName] = value
            }
        }
    }

    private fun inferParentFromName(name: String): String? {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(0, dotIndex) else null
    }

    private fun processApplicationElement(context: XmlContext, element: Element) {
        val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
            .takeIf { it.isNotEmpty() }
        applicationTheme = theme
    }

    private fun processActivityElement(context: XmlContext, element: Element) {
        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            .takeIf { it.isNotEmpty() && it in FIXED_ORIENTATIONS }
            ?: return

        val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
            .takeIf { it.isNotEmpty() }

        activitiesWithOrientation.add(
            ActivityInfo(
                element = element,
                orientation = orientation,
                themeName = theme,
                location = context.getLocation(element)
            )
        )
    }

    override fun afterCheckRootProject(context: Context) {
        targetSdkVersion = context.project.targetSdk

        if (targetSdkVersion < MIN_SDK_WITH_EXCEPTION) {
            return
        }

        for (activityInfo in activitiesWithOrientation) {
            val themeName = activityInfo.themeName ?: applicationTheme ?: continue
            val cleanThemeName = cleanThemeReference(themeName)

            if (isThemeTranslucent(cleanThemeName)) {
                val activityName = activityInfo.element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    .takeIf { it.isNotEmpty() }
                    ?: activityInfo.element.getAttribute(ATTR_NAME)
                    ?: "unknown"

                context.report(
                    issue = ISSUE,
                    location = activityInfo.location,
                    message = "Mixing screenOrientation with a translucent theme on an activity " +
                            "is not supported on apps with targetSdkVersion O or greater since " +
                            "there can be another activity visible behind your activity with a " +
                            "conflicting request. " +
                            "Such a conflict will result in a crash on devices running O or greater."
                )
            }
        }
    }

    private fun cleanThemeReference(themeRef: String): String {
        return when {
            themeRef.startsWith("@style/") -> themeRef.substring("@style/".length)
            themeRef.startsWith("@android:style/") -> themeRef.substring("@android:style/".length)
            themeRef.startsWith("?") -> themeRef.substring(1)
            else -> themeRef
        }
    }

    private fun isThemeTranslucent(themeName: String, visited: MutableSet<String> = mutableSetOf()): Boolean {
        if (themeName in visited) return false
        visited.add(themeName)

        themeTranslucencyCache[themeName]?.let { return it }

        // Check if this is a known Android framework translucent theme
        if (isKnownTranslucentFrameworkTheme(themeName)) {
            themeTranslucencyCache[themeName] = true
            return true
        }

        val attrs = themeAttributeMap[themeName]
        if (attrs != null) {
            val isTranslucentAttr = attrs[ATTR_WINDOW_IS_TRANSLUCENT]
            val isFloatingAttr = attrs[ATTR_WINDOW_IS_FLOATING]

            if (isTranslucentAttr == "true") {
                themeTranslucencyCache[themeName] = true
                return true
            }

            if (isFloatingAttr == "true") {
                themeTranslucencyCache[themeName] = true
                return true
            }

            if (isTranslucentAttr == "false" && isFloatingAttr == "false") {
                themeTranslucencyCache[themeName] = false
                return false
            }
        }

        // Check parent theme
        val parent = themeParentMap[themeName]
        if (parent != null) {
            val cleanParent = cleanThemeReference(parent)
            val parentResult = isThemeTranslucent(cleanParent, visited)
            themeTranslucencyCache[themeName] = parentResult
            return parentResult
        }

        themeTranslucencyCache[themeName] = false
        return false
    }

    private fun isKnownTranslucentFrameworkTheme(themeName: String): Boolean {
        val lowerName = themeName.lowercase()
        return lowerName.contains("translucent") ||
                lowerName.contains("floating") ||
                lowerName.contains("dialog") ||
                themeName == "Theme.Translucent" ||
                themeName == "Theme.Translucent.NoTitleBar" ||
                themeName == "Theme.Translucent.NoTitleBar.Fullscreen" ||
                themeName == "android:Theme.Translucent" ||
                themeName == "android:Theme.Translucent.NoTitleBar" ||
                themeName == "android:Theme.Translucent.NoTitleBar.Fullscreen" ||
                themeName == "Theme.Dialog" ||
                themeName == "android:Theme.Dialog" ||
                themeName == "Theme.AppCompat.Dialog" ||
                themeName == "Theme.AppCompat.Light.Dialog" ||
                themeName == "Theme.MaterialComponents.Dialog" ||
                themeName == "Theme.MaterialComponents.Light.Dialog"
    }
}