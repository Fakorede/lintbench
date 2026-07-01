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
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be another activity \
                visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind \
                your translucent activity requests portrait. In this case the system can only \
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
                EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)
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
    }

    /**
     * Set of style names (stripped of prefixes) that are translucent or floating.
     */
    private val translucentThemes = mutableSetOf<String>()

    /**
     * Map from style name to parent style name (both stripped of prefixes).
     */
    private val styleParents = mutableMapOf<String, String>()

    /**
     * Pending activities with fixed orientation: (theme, location) pairs.
     */
    private data class ActivityInfo(
        val theme: String?,
        val orientation: String,
        val location: Location
    )

    private val pendingActivities = mutableListOf<ActivityInfo>()
    private var applicationTheme: String? = null

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, TAG_APPLICATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_APPLICATION -> {
                val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                if (theme.isNotEmpty()) {
                    applicationTheme = stripThemePrefix(theme)
                }
            }
            TAG_ACTIVITY -> {
                val orientationAttr = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                if (orientationAttr.isNotEmpty() && isFixedOrientation(orientationAttr)) {
                    val themeAttr = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                    val theme = if (themeAttr.isNotEmpty()) stripThemePrefix(themeAttr) else null
                    val location = context.getElementLocation(element)
                    pendingActivities.add(ActivityInfo(theme, orientationAttr, location))
                }
            }
        }
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        // Only process values files (styles/themes)
        val parentFile = context.file.parentFile ?: return
        if (!parentFile.name.startsWith("values")) return

        val root = document.documentElement ?: return
        if (root.tagName != "resources") return

        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            if (node.tagName != "style") continue

            val styleName = node.getAttribute("name") ?: continue
            if (styleName.isEmpty()) continue
            val strippedName = stripThemePrefix(styleName)

            val parentAttr = node.getAttribute("parent") ?: ""
            val strippedParent = if (parentAttr.isNotEmpty()) {
                stripThemePrefix(parentAttr)
            } else {
                // Implicit parent from dot notation
                val dotIndex = styleName.lastIndexOf('.')
                if (dotIndex > 0) stripThemePrefix(styleName.substring(0, dotIndex)) else ""
            }

            if (strippedParent.isNotEmpty()) {
                styleParents[strippedName] = strippedParent
            }

            // Check if this style itself declares windowIsTranslucent or windowIsFloating = true
            val items = node.childNodes
            for (j in 0 until items.length) {
                val item = items.item(j) as? Element ?: continue
                if (item.tagName != "item") continue
                val itemName = item.getAttribute("name") ?: continue
                val itemValue = item.textContent?.trim() ?: continue

                val localName = itemName.removePrefix("android:")
                if ((localName == "windowIsTranslucent" || localName == "windowIsFloating") &&
                    itemValue.equals("true", ignoreCase = true)
                ) {
                    translucentThemes.add(strippedName)
                    break
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val targetSdk = context.mainProject.targetSdk
        if (targetSdk < 26) { // O = API 26
            return
        }

        // Resolve transitive translucency through parent chains
        for (activity in pendingActivities) {
            val effectiveTheme = activity.theme ?: applicationTheme ?: continue
            if (isTranslucentTheme(effectiveTheme)) {
                context.report(
                    ISSUE,
                    activity.location,
                    "Should not specify a fixed `screenOrientation` with a translucent or " +
                            "floating theme on API 26 and above; this combination is not supported " +
                            "and the activity will crash"
                )
            }
        }
    }

    private fun isTranslucentTheme(themeName: String): Boolean {
        val visited = mutableSetOf<String>()
        var current: String? = themeName
        while (current != null && visited.add(current)) {
            if (translucentThemes.contains(current)) return true
            if (isKnownTranslucentByName(current)) return true
            current = styleParents[current]
        }
        return false
    }

    private fun isKnownTranslucentByName(name: String): Boolean {
        // Well-known Android translucent/floating theme names
        return name.equals("Theme.Translucent", ignoreCase = true) ||
                name.equals("Theme.Translucent.NoTitleBar", ignoreCase = true) ||
                name.equals("Theme.Translucent.NoTitleBar.Fullscreen", ignoreCase = true) ||
                name.contains("Translucent", ignoreCase = true) ||
                name.contains("Floating", ignoreCase = true)
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return FIXED_ORIENTATIONS.contains(orientation)
    }

    private fun stripThemePrefix(theme: String): String {
        return theme
            .removePrefix("@android:style/")
            .removePrefix("@*android:style/")
            .removePrefix("@style/")
            .trim()
    }
}