package com.android.tools.lint.checks

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
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), XmlScanner {

    private val activities = mutableListOf<ActivityInfo>()
    private val styles = mutableMapOf<String, StyleInfo>()
    private var applicationTheme: String? = null
    private var explicitTargetSdk: Int? = null

    class ActivityInfo(
        val theme: String?,
        val orientation: String,
        val location: Location
    )

    class StyleInfo(
        val name: String,
        val parent: String?,
        var isTranslucent: Boolean
    )

    override fun getApplicableElements(): Collection<String> {
        return listOf("activity", "style", "application", "uses-sdk")
    }

    override fun beforeCheckProject(context: Context) {
        activities.clear()
        styles.clear()
        applicationTheme = null
        explicitTargetSdk = null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "application" -> {
                val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                    .takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("android:theme")
                if (theme.isNotEmpty()) {
                    applicationTheme = theme
                }
            }
            "activity" -> {
                val orientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "screenOrientation")
                    .takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("android:screenOrientation")
                if (orientation.isNotEmpty() && FIXED_ORIENTATIONS.contains(orientation)) {
                    val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                        .takeIf { it.isNotEmpty() }
                        ?: element.getAttribute("android:theme")
                        .takeIf { it.isNotEmpty() }
                    val attr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "screenOrientation")
                        ?: element.getAttributeNode("android:screenOrientation")
                    val location = context.getLocation(attr ?: element)
                    activities.add(ActivityInfo(theme, orientation, location))
                }
            }
            "uses-sdk" -> {
                val targetSdkAttr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "targetSdkVersion")
                    .takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("android:targetSdkVersion")
                if (targetSdkAttr.isNotEmpty()) {
                    explicitTargetSdk = targetSdkAttr.toIntOrNull()
                }
            }
            "style" -> {
                val name = element.getAttribute("name")
                if (name.isNotEmpty()) {
                    var parent = element.getAttribute("parent").takeIf { it.isNotEmpty() }
                    if (parent == null && name.contains('.')) {
                        parent = name.substringBeforeLast('.')
                    }
                    var isTranslucent = false
                    val childNodes = element.childNodes
                    for (i in 0 until childNodes.length) {
                        val child = childNodes.item(i)
                        if (child is Element && child.tagName == "item") {
                            val itemName = child.getAttribute("name")
                            if (itemName == "android:windowIsTranslucent" ||
                                itemName == "android:windowSwipeToDismiss" ||
                                itemName == "android:windowIsFloating" ||
                                itemName == "windowIsTranslucent" ||
                                itemName == "windowSwipeToDismiss" ||
                                itemName == "windowIsFloating"
                            ) {
                                val value = child.textContent?.trim()
                                if (value == "true") {
                                    isTranslucent = true
                                }
                            }
                        }
                    }
                    styles[normalizeStyleName(name)] = StyleInfo(name, parent?.let { normalizeStyleName(it) }, isTranslucent)
                }
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        val targetSdk = explicitTargetSdk ?: context.project.targetSdkVersion.apiLevel
        if (targetSdk in 2..25) return

        for (activity in activities) {
            val themeRef = activity.theme ?: applicationTheme
            if (themeRef != null) {
                val normalizedTheme = normalizeStyleName(themeRef)
                if (isThemeTranslucent(normalizedTheme, mutableSetOf())) {
                    context.report(
                        ISSUE,
                        activity.location,
                        "An activity with a translucent theme should not request a fixed screen orientation."
                    )
                }
            }
        }
    }

    private fun isThemeTranslucent(themeName: String, visited: MutableSet<String>): Boolean {
        if (!visited.add(themeName)) return false

        if (isNameTranslucent(themeName)) return true

        val styleInfo = styles[themeName]
        if (styleInfo != null) {
            if (styleInfo.isTranslucent) return true
            if (styleInfo.parent != null) {
                return isThemeTranslucent(styleInfo.parent, visited)
            }
        }
        return false
    }

    private fun isNameTranslucent(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("translucent") ||
                lower.contains("dialog") ||
                lower.contains("floating") ||
                lower.contains("nodisplay") ||
                lower.contains("transparent") ||
                lower.contains("wallpaper")
    }

    private fun normalizeStyleName(ref: String): String {
        return ref.substringAfter('/')
    }

    companion object {
        private val FIXED_ORIENTATIONS = setOf(
            "portrait", "landscape",
            "reversePortrait", "reverseLandscape",
            "sensorPortrait", "sensorLandscape",
            "userPortrait", "userLandscape",
            "locked"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with targetSdkVersion O or greater since there can be an another activity \
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