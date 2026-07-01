package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element

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
        private const val ATTR_THEME = "theme"
        private const val ATTR_WINDOW_IS_TRANSLUCENT = "windowIsTranslucent"
        private const val ATTR_WINDOW_IS_FLOATING = "windowIsFloating"
        private const val TAG_STYLE = "style"
        private const val TAG_ITEM = "item"

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
            "Transparent",
            "Dialog",
            "Float"
        )
    }

    // Map from style name -> map of attribute name -> value
    // Collected from res/values/styles.xml files
    private val styleAttributes = mutableMapOf<String, Map<String, String>>()
    // Map from style name -> parent style name
    private val styleParents = mutableMapOf<String, String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, TAG_APPLICATION, TAG_STYLE)
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        // Collect style information from values files
        if (context.resourceFolderType == ResourceFolderType.VALUES) {
            val root = document.documentElement ?: return
            val children = root.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node is Element && node.tagName == TAG_STYLE) {
                    collectStyle(node)
                }
            }
        }
    }

    private fun collectStyle(styleElement: Element) {
        val name = styleElement.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val parent = styleElement.getAttribute("parent")

        val attrs = mutableMapOf<String, String>()
        val items = styleElement.childNodes
        for (i in 0 until items.length) {
            val item = items.item(i)
            if (item is Element && item.tagName == TAG_ITEM) {
                val itemName = item.getAttribute(ATTR_NAME)
                val itemValue = item.textContent?.trim() ?: continue
                if (itemName.isNotEmpty()) {
                    // Strip android: prefix if present
                    val cleanName = itemName.removePrefix("android:")
                    attrs[cleanName] = itemValue
                }
            }
        }

        styleAttributes[name] = attrs

        // Determine parent: explicit parent attribute, or dot-notation
        val resolvedParent = when {
            parent.isNotEmpty() -> parent
            name.contains('.') -> name.substringBeforeLast('.')
            else -> null
        }
        if (resolvedParent != null) {
            styleParents[name] = resolvedParent
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when {
            context.resourceFolderType == ResourceFolderType.VALUES && element.tagName == TAG_STYLE -> {
                collectStyle(element)
            }
            context.resourceFolderType == null -> {
                // Manifest file
                handleManifestElement(context, element)
            }
        }
    }

    private fun handleManifestElement(context: XmlContext, element: Element) {
        val tagName = element.tagName ?: return

        if (tagName == TAG_ACTIVITY) {
            handleActivity(context, element)
        }
    }

    private fun handleActivity(context: XmlContext, element: Element) {
        val project = context.project
        val targetSdk = project.targetSdk
        if (targetSdk < 26) {
            return
        }

        // Check screenOrientation
        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            ?: return
        val orientation = orientationAttr.value
        if (!isFixedOrientation(orientation)) {
            return
        }

        // Get theme: first from activity, then from application
        val activityTheme = element.getAttributeNS(ANDROID_URI, ATTR_THEME).takeIf { it.isNotEmpty() }
        val applicationTheme = getApplicationTheme(element)
        val theme = activityTheme ?: applicationTheme ?: return

        if (!isTranslucentTheme(theme)) {
            return
        }

        val activityName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        val message = "Mixing screenOrientation with a translucent theme is not supported on API 26+; " +
                "this will throw an exception"

        context.report(
            issue = ISSUE,
            scope = element,
            location = context.getLocation(element),
            message = message
        )
    }

    private fun getApplicationTheme(activityElement: Element): String? {
        val parent = activityElement.parentNode as? Element ?: return null
        if (parent.tagName != TAG_APPLICATION) return null
        return parent.getAttributeNS(ANDROID_URI, ATTR_THEME).takeIf { it.isNotEmpty() }
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        if (orientation.startsWith("@") || orientation.startsWith("?")) {
            return false
        }
        return FIXED_ORIENTATIONS.contains(orientation)
    }

    private fun isTranslucentTheme(themeRef: String): Boolean {
        val themeName = extractStyleName(themeRef) ?: return false

        // Check by name patterns first (for framework themes)
        if (isTranslucentByName(themeName)) {
            return true
        }

        // Check by looking up style attributes
        return isTranslucentByAttributes(themeName, mutableSetOf())
    }

    private fun isTranslucentByName(name: String): Boolean {
        return TRANSLUCENT_THEME_NAME_PATTERNS.any { pattern ->
            name.contains(pattern, ignoreCase = false)
        }
    }

    private fun isTranslucentByAttributes(styleName: String, visited: MutableSet<String>): Boolean {
        if (styleName in visited) return false
        visited.add(styleName)

        val attrs = styleAttributes[styleName]
        if (attrs != null) {
            val isTranslucent = attrs[ATTR_WINDOW_IS_TRANSLUCENT]
            val isFloating = attrs[ATTR_WINDOW_IS_FLOATING]
            if (isTranslucent == "true" || isFloating == "true") {
                return true
            }
            // If explicitly false, not translucent (don't check parent for these)
        }

        // Check parent
        val parent = styleParents[styleName] ?: return false
        val parentName = extractStyleName(parent) ?: parent

        // Check parent name patterns
        if (isTranslucentByName(parentName)) {
            return true
        }

        return isTranslucentByAttributes(parentName, visited)
    }

    private fun extractStyleName(themeRef: String): String? {
        // "@style/Theme.MyApp" -> "Theme.MyApp"
        // "@android:style/Theme.Translucent" -> "Theme.Translucent"
        // "?attr/..." -> null
        if (themeRef.startsWith("?")) return null
        val slashIndex = themeRef.lastIndexOf('/')
        return if (slashIndex >= 0) {
            themeRef.substring(slashIndex + 1)
        } else {
            themeRef.removePrefix("@")
        }
    }
}