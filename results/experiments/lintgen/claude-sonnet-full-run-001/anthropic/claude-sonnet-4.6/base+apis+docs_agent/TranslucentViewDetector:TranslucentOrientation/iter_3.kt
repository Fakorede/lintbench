package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
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
        @JvmField
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
        private const val ATTR_THEME = "theme"

        // Orientation values that are "fixed" (not sensor/unspecified/etc.)
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

        private fun isTranslucentThemeName(theme: String): Boolean {
            val lower = theme.lowercase()
            return lower.contains("translucent") ||
                lower.contains("dialog") ||
                lower.contains("floating")
        }
    }

    // Application-level theme
    private var applicationTheme: String? = null

    // Map from style name variants to whether they are translucent
    // Key: normalized style name (without @style/ prefix), Value: true if translucent
    private val styleTranslucency = mutableMapOf<String, Boolean>()

    // Style parent relationships: normalized name -> normalized parent name
    private val styleParents = mutableMapOf<String, String>()

    // Pending activity checks
    private data class PendingActivity(
        val context: XmlContext,
        val element: Element,
        val orientation: String,
        val theme: String
    )

    private val pendingActivities = mutableListOf<PendingActivity>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, TAG_APPLICATION, "style")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_APPLICATION -> {
                val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                if (theme.isNotEmpty()) {
                    applicationTheme = theme
                }
            }
            TAG_ACTIVITY -> {
                visitActivityElement(context, element)
            }
            "style" -> {
                visitStyleElement(element)
            }
        }
    }

    private fun visitActivityElement(context: XmlContext, element: Element) {
        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            ?: return
        val orientation = orientationAttr.value
        if (orientation.isEmpty() || orientation !in FIXED_ORIENTATIONS) return

        val activityTheme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
        val theme = if (activityTheme.isNotEmpty()) activityTheme else (applicationTheme ?: return)

        // Add to pending list; will be resolved after all files are scanned
        pendingActivities.add(PendingActivity(context, element, orientation, theme))
    }

    private fun normalizeStyleName(name: String): String {
        return when {
            name.startsWith("@style/") -> name.removePrefix("@style/")
            name.startsWith("@android:style/") -> name // keep as-is for android styles
            else -> name
        }
    }

    private fun visitStyleElement(element: Element) {
        val styleName = element.getAttribute(ATTR_NAME)
        if (styleName.isEmpty()) return

        val normalizedName = normalizeStyleName(styleName)

        // Check parent attribute
        val parent = element.getAttribute("parent")
        if (parent.isNotEmpty()) {
            val normalizedParent = normalizeStyleName(parent)
            styleParents[normalizedName] = normalizedParent
        } else {
            // Implicit parent via dot notation (e.g., "Theme.MyApp.Translucent" -> parent is "Theme.MyApp")
            val dotIndex = normalizedName.lastIndexOf('.')
            if (dotIndex > 0) {
                val implicitParent = normalizedName.substring(0, dotIndex)
                styleParents[normalizedName] = implicitParent
            }
        }

        // Check item children for windowIsTranslucent or windowIsFloating
        val children = element.childNodes
        var hasTranslucentItem = false
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName != "item") continue
            val itemName = child.getAttribute(ATTR_NAME)
            val itemValue = child.textContent?.trim() ?: ""
            if ((itemName == "android:windowIsTranslucent" || itemName == "windowIsTranslucent" ||
                        itemName == "android:windowIsFloating" || itemName == "windowIsFloating") &&
                itemValue.equals("true", ignoreCase = true)
            ) {
                hasTranslucentItem = true
                break
            }
        }

        if (hasTranslucentItem) {
            styleTranslucency[normalizedName] = true
        }
    }

    private fun isStyleTranslucent(styleName: String, visited: MutableSet<String> = mutableSetOf()): Boolean {
        if (!visited.add(styleName)) return false // cycle detection

        // Check if we have explicit translucency info
        if (styleTranslucency[styleName] == true) return true

        // Check name heuristic
        if (isTranslucentThemeName(styleName)) return true

        // Check parent chain
        val parent = styleParents[styleName]
        if (parent != null) {
            return isStyleTranslucent(parent, visited)
        }

        return false
    }

    private fun isThemeTranslucent(theme: String): Boolean {
        // Check name heuristic directly
        if (isTranslucentThemeName(theme)) return true

        val normalized = normalizeStyleName(theme)

        // Check name heuristic on normalized
        if (isTranslucentThemeName(normalized)) return true

        // Check style definitions
        return isStyleTranslucent(normalized)
    }

    override fun afterCheckRootProject(context: Context) {
        // Process all pending activities now that all files have been scanned
        for (pending in pendingActivities) {
            if (isThemeTranslucent(pending.theme)) {
                val orientationAttr = pending.element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                if (orientationAttr != null) {
                    val location = pending.context.getValueLocation(orientationAttr)
                    pending.context.report(
                        ISSUE,
                        pending.element,
                        location,
                        "Combining a fixed orientation (currently `${pending.orientation}`) with a " +
                            "translucent theme (`${pending.theme}`) is not allowed, and can cause a " +
                            "crash on devices running API 26 and higher"
                    )
                }
            }
        }
        pendingActivities.clear()
        applicationTheme = null
        styleTranslucency.clear()
        styleParents.clear()
    }
}