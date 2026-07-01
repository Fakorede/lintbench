package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), Detector.XmlScanner {

    private val activities = mutableListOf<ActivityInfo>()
    private val translucentStyles = mutableSetOf<String>()
    private val styleParents = mutableMapOf<String, String>()

    private class ActivityInfo(
        val theme: String?,
        val location: Location
    )

    override fun beforeCheckRootProject(context: Context) {
        activities.clear()
        translucentStyles.clear()
        styleParents.clear()
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("activity", "style")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName == "activity") {
            val root = element.ownerDocument.documentElement
            if (root.tagName == "manifest") {
                val orientation = element.getAttributeNS(ANDROID_URI, "screenOrientation")
                if (orientation.isNotEmpty() && isFixedOrientation(orientation)) {
                    var theme = element.getAttributeNS(ANDROID_URI, "theme")
                    if (theme.isEmpty()) {
                        val application = element.parentNode as? Element
                        if (application != null && application.tagName == "application") {
                            theme = application.getAttributeNS(ANDROID_URI, "theme")
                        }
                    }
                    if (theme.isNotEmpty()) {
                        val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, "screenOrientation")
                        val location = if (orientationAttr != null) {
                            context.getLocation(orientationAttr)
                        } else {
                            context.getLocation(element)
                        }
                        activities.add(ActivityInfo(theme, location))
                    }
                }
            }
        } else if (tagName == "style") {
            val name = element.getAttribute("name")
            if (name.isNotEmpty()) {
                val styleName = cleanStyleName(name)
                val parent = element.getAttribute("parent")
                if (parent.isNotEmpty()) {
                    styleParents[styleName] = cleanStyleName(parent)
                } else if (styleName.contains('.')) {
                    val lastDot = styleName.lastIndexOf('.')
                    styleParents[styleName] = styleName.substring(0, lastDot)
                }

                val childNodes = element.childNodes
                var isTranslucent = false
                for (i in 0 until childNodes.length) {
                    val child = childNodes.item(i)
                    if (child is Element && child.tagName == "item") {
                        val itemName = child.getAttribute("name")
                        val itemValue = child.textContent?.trim()
                        if ((itemName == "android:windowIsTranslucent" ||
                             itemName == "android:windowIsFloating" ||
                             itemName == "android:windowSwipeToDismiss") &&
                            itemValue == "true") {
                            isTranslucent = true
                        }
                    }
                }
                if (isTranslucent) {
                    translucentStyles.add(styleName)
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val targetSdkVersion = context.project.targetSdkVersion.apiLevel
        if (targetSdkVersion < 26) return

        for (activity in activities) {
            val theme = activity.theme ?: continue
            val cleanTheme = cleanStyleName(theme)
            if (isTranslucent(cleanTheme)) {
                context.report(
                    ISSUE,
                    activity.location,
                    "Only fullscreen opaque activities can request orientation"
                )
            }
        }
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return orientation in setOf(
            "portrait", "landscape",
            "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape",
            "userPortrait", "userLandscape",
            "locked"
        )
    }

    private fun cleanStyleName(style: String): String {
        return style
            .removePrefix("@style/")
            .removePrefix("@android:style/")
            .removePrefix("?")
            .replace(':', '.')
    }

    private fun isTranslucent(themeName: String): Boolean {
        var current: String? = themeName
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current)) {
            if (defaultTranslucentThemes.any { current!!.contains(it) } ||
                current!!.contains("Translucent") ||
                current!!.contains("Dialog") ||
                current!!.contains("Floating")) {
                return true
            }
            if (translucentStyles.contains(current)) {
                return true
            }
            current = styleParents[current]
        }
        return false
    }

    companion object {
        private val defaultTranslucentThemes = setOf(
            "Theme.Translucent",
            "Theme.Translucent.NoTitleBar",
            "Theme.Translucent.NoTitleBar.Fullscreen",
            "Theme.Dialog",
            "Theme.Panel",
            "Theme.Material.Dialog",
            "Theme.Material.Light.Dialog",
            "Theme.AppCompat.Dialog",
            "Theme.AppCompat.Light.Dialog",
            "Theme.AppCompat.Dialog.Alert",
            "Theme.MaterialComponents.Dialog",
            "Theme.MaterialComponents.Light.Dialog"
        )

        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Activity specifies both a fixed orientation and a translucent theme",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )
    }
}