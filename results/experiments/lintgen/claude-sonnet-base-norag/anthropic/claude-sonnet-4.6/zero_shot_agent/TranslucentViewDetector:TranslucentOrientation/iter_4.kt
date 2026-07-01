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

        private val TRANSLUCENT_THEME_NAME_PATTERNS = listOf(
            "Translucent",
            "translucent",
            "Dialog",
            "Floating",
            "floating"
        )
    }

    // Maps style name -> parent style name (explicit parent attribute)
    private val styleParentMap = mutableMapOf<String, String?>()
    // Maps style name -> map of item name -> item value
    private val styleAttributeMap = mutableMapOf<String, MutableMap<String, String>>()
    // Cache for translucency check results
    private val translucentStyleCache = mutableMapOf<String, Boolean>()

    private var applicationTheme: String? = null

    // Pending activities to check after all resource files have been processed
    // Each entry: Triple(context, element, effectiveTheme)
    private val pendingActivityChecks = mutableListOf<Triple<XmlContext, Element, String>>()

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
        if (activityTheme.isNotEmpty()) {
            checkActivityTheme(context, element, activityTheme)
        } else {
            // Store for later - we need the application theme
            pendingActivityChecks.add(Triple(context, element, ""))
        }
    }

    private fun checkActivityTheme(context: XmlContext, element: Element, theme: String) {
        if (isTranslucentTheme(theme)) {
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

    override fun afterCheckFile(context: com.android.tools.lint.detector.api.Context) {
        // After processing the manifest, handle any pending checks using the application theme
        if (pendingActivityChecks.isNotEmpty()) {
            val appTheme = applicationTheme
            if (appTheme != null) {
                for ((xmlContext, element, _) in pendingActivityChecks) {
                    checkActivityTheme(xmlContext, element, appTheme)
                }
            }
            pendingActivityChecks.clear()
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        // Final cleanup
        pendingActivityChecks.clear()
        translucentStyleCache.clear()
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        val normalized = normalizeThemeName(theme)
        return translucentStyleCache.getOrPut(normalized) {
            isTranslucentThemeInternal(normalized, mutableSetOf())
        }
    }

    private fun isTranslucentThemeInternal(themeName: String, visited: MutableSet<String>): Boolean {
        if (!visited.add(themeName)) return false

        // Check if the theme name itself contains translucent patterns
        for (pattern in TRANSLUCENT_THEME_NAME_PATTERNS) {
            if (themeName.contains(pattern)) return true
        }

        // Check style attributes defined in resource files
        val attrs = styleAttributeMap[themeName]
        if (attrs != null) {
            val translucentAttrs = setOf(
                "windowIsTranslucent",
                "android:windowIsTranslucent",
                "windowSwipeToDismiss",
                "android:windowSwipeToDismiss"
            )
            for (translucentAttr in translucentAttrs) {
                val value = attrs[translucentAttr]
                if (value == "true") return true
            }
        }

        // Check explicit parent
        val parent = styleParentMap[themeName]
        if (parent != null) {
            val normalizedParent = normalizeThemeName(parent)
            if (normalizedParent.isNotEmpty() && isTranslucentThemeInternal(normalizedParent, visited)) return true
        }

        // Check implicit parent via dot notation
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