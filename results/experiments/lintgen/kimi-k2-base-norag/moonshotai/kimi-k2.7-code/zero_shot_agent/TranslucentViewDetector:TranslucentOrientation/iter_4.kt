package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_TARGET_SDK_VERSION
import com.android.SdkConstants.ATTR_THEME
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class TranslucentViewDetector : ResourceXmlDetector() {

    companion object {
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported
                on apps with targetSdkVersion O or greater, since there can be another activity
                visible behind your activity with a conflicting request.

                Devices running platform version O or greater will throw an exception if this
                state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_SCOPE,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val WINDOW_IS_TRANSLUCENT = "windowIsTranslucent"
        private const val TARGET_SDK_O = 26
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

    private var targetSdkVersion: Int = -1
    private var applicationTheme: String? = null
    private val activities = mutableListOf<ActivityInfo>()
    private val styles = mutableMapOf<String, StyleInfo>()

    private data class ActivityInfo(
        val element: Element,
        val context: XmlContext,
        val theme: String?,
        val orientation: String?
    )

    private data class StyleInfo(
        val parent: String?,
        val items: Map<String, String>
    )

    override fun getApplicableElements(): Collection<String> = listOf(
        "uses-sdk",
        "application",
        "activity",
        "style",
        "item"
    )

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "uses-sdk" -> {
                val targetSdk = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
                if (targetSdk.isNotBlank()) {
                    targetSdkVersion = targetSdk.toIntOrNull() ?: -1
                }
            }
            "application" -> {
                applicationTheme = element.getAttributeNS(ANDROID_URI, ATTR_THEME).resolveStyleRef()
            }
            "activity" -> {
                val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME).resolveStyleRef()
                val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                activities.add(ActivityInfo(element, context, theme, orientation))
            }
            "style" -> {
                val name = element.getAttribute(ATTR_NAME)
                if (name.isNotBlank()) {
                    val parent = element.getAttribute(ATTR_PARENT).resolveStyleRef()
                        ?: deriveParentFromName(name)
                    val items = mutableMapOf<String, String>()
                    val children = element.childNodes
                    for (i in 0 until children.length) {
                        val child = children.item(i)
                        if (child is Element && child.tagName == "item") {
                            val itemName = child.getAttribute(ATTR_NAME)
                                .substringAfterLast(":")
                                .trim()
                            val value = child.textContent?.trim() ?: ""
                            if (itemName.isNotBlank()) {
                                items[itemName] = value
                            }
                        }
                    }
                    styles[name] = StyleInfo(parent, items)
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (targetSdkVersion < TARGET_SDK_O) {
            resetState()
            return
        }

        for (activity in activities) {
            val orientation = activity.orientation
            if (orientation.isNullOrBlank() || !isFixedOrientation(orientation)) {
                continue
            }

            val theme = activity.theme ?: applicationTheme ?: continue
            if (isTranslucent(theme)) {
                activity.context.report(
                    ISSUE,
                    activity.element,
                    activity.context.getLocation(activity.element),
                    "This activity's android:screenOrientation is fixed, but its theme is " +
                            "translucent; this is not supported on apps with targetSdkVersion >= O"
                )
            }
        }

        resetState()
    }

    private fun resetState() {
        targetSdkVersion = -1
        applicationTheme = null
        activities.clear()
        styles.clear()
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return orientation.split("|").any { part ->
            FIXED_ORIENTATIONS.contains(part.trim())
        }
    }

    private fun isTranslucent(themeName: String): Boolean {
        var current: String? = themeName
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current)) {
            val style = styles[current] ?: return false
            val value = style.items[WINDOW_IS_TRANSLUCENT]
            if (value == "true" || value == "1" || value == "@bool/true") {
                return true
            }
            current = style.parent ?: deriveParentFromName(current)
        }
        return false
    }

    private fun deriveParentFromName(name: String): String? {
        val lastDot = name.lastIndexOf('.')
        return if (lastDot > 0) name.substring(0, lastDot) else null
    }

    private fun String.resolveStyleRef(): String? {
        return when {
            startsWith("@style/") -> substring(7)
            startsWith("@android:style/") -> substring("@android:style/".length)
            startsWith("?") -> null
            isBlank() -> null
            else -> this
        }
    }
}