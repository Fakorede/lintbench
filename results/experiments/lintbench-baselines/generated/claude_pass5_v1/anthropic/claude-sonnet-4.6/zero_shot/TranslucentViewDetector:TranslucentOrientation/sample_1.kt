package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.ResourceXmlDetector
import org.w3c.dom.Element
import com.android.tools.lint.detector.api.Project
import com.android.resources.ResourceFolderType
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.EnumSet

/**
 * Detector that checks for activities that combine a fixed screen orientation
 * with a translucent theme, which causes a crash on Android O and higher.
 */
class TranslucentViewDetector : ResourceXmlDetector() {

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
                EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.MANIFEST),
                EnumSet.of(Scope.ALL_RESOURCE_FILES),
                EnumSet.of(Scope.MANIFEST)
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

        private val TRANSLUCENT_STYLES = setOf(
            "Theme.Translucent",
            "Theme.Translucent.NoTitleBar",
            "Theme.Translucent.NoTitleBar.Fullscreen",
            "android:Theme.Translucent",
            "android:Theme.Translucent.NoTitleBar",
            "android:Theme.Translucent.NoTitleBar.Fullscreen"
        )

        private const val ATTR_WINDOW_IS_TRANSLUCENT = "windowIsTranslucent"
        private const val ATTR_WINDOW_IS_FLOATING = "windowIsFloating"
    }

    // Map from theme name to whether it's translucent (from style resources)
    private val themeTranslucencyCache = mutableMapOf<String, Boolean>()

    // Map from activity name to its theme (from manifest)
    private val activityThemes = mutableMapOf<String, String>()

    // Map from activity name to its orientation (from manifest)
    private val activityOrientations = mutableMapOf<String, String>()

    // Application-level theme
    private var applicationTheme: String? = null

    // Store activity elements with orientation for later checking
    private val activityElements = mutableListOf<Pair<Element, XmlContext>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, TAG_APPLICATION, "style")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "style" -> visitStyleElement(context, element)
            TAG_APPLICATION -> visitApplicationElement(context, element)
            TAG_ACTIVITY -> visitActivityElement(context, element)
        }
    }

    private fun visitStyleElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            .takeIf { it.isNotEmpty() }
            ?: element.getAttribute(ATTR_NAME)
        if (name.isEmpty()) return

        // Check if this style has windowIsTranslucent or windowIsFloating set to true
        var isTranslucent = false
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "item") {
                val itemElement = child as Element
                val itemName = itemElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    .takeIf { it.isNotEmpty() }
                    ?: itemElement.getAttribute(ATTR_NAME)
                val value = itemElement.textContent?.trim() ?: ""
                if ((itemName == ATTR_WINDOW_IS_TRANSLUCENT || itemName == "android:$ATTR_WINDOW_IS_TRANSLUCENT" ||
                            itemName == ATTR_WINDOW_IS_FLOATING || itemName == "android:$ATTR_WINDOW_IS_FLOATING") &&
                    value.equals("true", ignoreCase = true)
                ) {
                    isTranslucent = true
                    break
                }
            }
        }

        if (isTranslucent) {
            themeTranslucencyCache[name] = true
        }

        // Also check parent theme
        val parent = element.getAttribute("parent")
        if (parent.isNotEmpty()) {
            val parentStripped = parent.removePrefix("@style/")
                .removePrefix("@android:style/")
                .removePrefix("android:")
            if (TRANSLUCENT_STYLES.any { it.endsWith(parentStripped) || it == parent }) {
                themeTranslucencyCache[name] = true
            }
        }
    }

    private fun visitApplicationElement(context: XmlContext, element: Element) {
        val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
        if (theme.isNotEmpty()) {
            applicationTheme = theme
        }
    }

    private fun visitActivityElement(context: XmlContext, element: Element) {
        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        if (orientation.isEmpty() || orientation !in FIXED_ORIENTATIONS) return

        // Check target SDK
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < 26) return // Only O (API 26) and above

        val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
        val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)

        // Store for later analysis
        activityElements.add(Pair(element, context))

        val effectiveTheme = if (theme.isNotEmpty()) theme else applicationTheme

        if (effectiveTheme != null && isTranslucentTheme(effectiveTheme, context)) {
            reportIssue(context, element, activityName, orientation, effectiveTheme)
        }
    }

    private fun isTranslucentTheme(theme: String, context: XmlContext): Boolean {
        // Strip resource reference prefix
        val themeName = theme
            .removePrefix("@style/")
            .removePrefix("@android:style/")
            .removePrefix("@*android:style/")

        // Check against known translucent themes
        if (TRANSLUCENT_STYLES.any { it == theme || it == themeName || it.endsWith(themeName) }) {
            return true
        }

        // Check our cache from parsed style resources
        if (themeTranslucencyCache[themeName] == true) {
            return true
        }

        // Check with full theme name variants
        if (themeTranslucencyCache[theme] == true) {
            return true
        }

        return false
    }

    private fun reportIssue(
        context: XmlContext,
        element: Element,
        activityName: String,
        orientation: String,
        theme: String
    ) {
        val message = "Should not specify screen orientation with a translucent or floating " +
                "theme on activity `${activityName.ifEmpty { "(unknown)" }}`. " +
                "This combination is not supported on API level 26 and above and will " +
                "cause an exception to be thrown."

        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        val location = if (orientationAttr != null) {
            context.getLocation(orientationAttr)
        } else {
            context.getLocation(element)
        }

        context.report(
            issue = ISSUE,
            scope = element,
            location = location,
            message = message
        )
    }

    override fun afterCheckFile(context: Context) {
        // Nothing needed here
    }
}