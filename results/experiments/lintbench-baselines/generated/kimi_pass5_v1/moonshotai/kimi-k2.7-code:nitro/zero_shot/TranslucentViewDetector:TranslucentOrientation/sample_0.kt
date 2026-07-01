package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.ATTR_WINDOW_IS_TRANSLUCENT
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

private const val ANDROID_O_API_LEVEL = 26
private const val TRANSLUCENT = "Translucent"

class TranslucentViewDetector : Detector(), XmlScanner {

    private data class StyleInfo(
        val name: String,
        val parentName: String?,
        val declaresTranslucent: Boolean
    )

    private data class ActivityInfo(
        val context: XmlContext,
        val element: Element,
        val orientation: String,
        val themeRef: String
    )

    private val styles = mutableMapOf<String, StyleInfo>()
    private val pendingActivities = mutableListOf<ActivityInfo>()

    override fun getApplicableElements(): Collection<String> = listOf(TAG_STYLE, TAG_ACTIVITY)

    override fun beforeCheckRootProject(context: Context) {
        styles.clear()
        pendingActivities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_STYLE -> parseStyle(element)
            TAG_ACTIVITY -> inspectActivity(context, element)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val translucent = computeTranslucentStyles()
        for (activity in pendingActivities) {
            if (isTranslucentTheme(activity.themeRef, translucent)) {
                val message = "Mixing a fixed screen orientation (\"${activity.orientation}\") " +
                        "with a translucent theme is not supported when targetSdkVersion is " +
                        "$ANDROID_O_API_LEVEL or greater. Remove the screenOrientation " +
                        "attribute or use a non-translucent theme."
                activity.context.report(
                    ISSUE,
                    activity.element,
                    activity.context.getElementLocation(activity.element),
                    message
                )
            }
        }
    }

    private fun parseStyle(element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeUnless { it.isBlank() } ?: return
        val parent = normalizeParent(element.getAttribute(ATTR_PARENT))
        val declaresTranslucent = (0 until element.childNodes.length).any { i ->
            val child = element.childNodes.item(i)
            child is Element &&
                    child.tagName == TAG_ITEM &&
                    child.getAttribute(ATTR_NAME).endsWith(ATTR_WINDOW_IS_TRANSLUCENT) &&
                    child.textContent.trim().toBoolean()
        }
        styles[name] = StyleInfo(name, parent, declaresTranslucent)
    }

    private fun inspectActivity(context: XmlContext, element: Element) {
        if (context.mainProject.targetSdkVersion < ANDROID_O_API_LEVEL) {
            return
        }

        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
            .takeUnless { it.isBlank() } ?: return
        if (!isFixedOrientation(orientation)) {
            return
        }

        val activityTheme = element.getAttributeNS(ANDROID_URI, ATTR_THEME).takeUnless { it.isBlank() }
        val applicationTheme = getApplicationTheme(element)
        val themeRef = activityTheme ?: applicationTheme ?: return

        pendingActivities.add(ActivityInfo(context, element, orientation, themeRef))
    }

    private fun getApplicationTheme(activity: Element): String? {
        val doc = activity.ownerDocument ?: return null
        val apps = doc.getElementsByTagName(TAG_APPLICATION)
        if (apps.length == 0) return null
        val app = apps.item(0) as? Element ?: return null
        return app.getAttributeNS(ANDROID_URI, ATTR_THEME).takeUnless { it.isBlank() }
    }

    private fun isFixedOrientation(value: String): Boolean {
        return value !in NON_FIXED_ORIENTATIONS
    }

    private fun computeTranslucentStyles(): Set<String> {
        val translucent = styles.values
            .filter { it.declaresTranslucent || it.name.contains(TRANSLUCENT) }
            .map { it.name }
            .toMutableSet()

        var changed: Boolean
        do {
            changed = false
            for (info in styles.values) {
                if (info.name in translucent) continue
                val parents = listOfNotNull(info.parentName, implicitParent(info.name))
                for (parent in parents) {
                    if (parent in translucent ||
                        parent.contains(TRANSLUCENT) ||
                        parent in KNOWN_TRANSLUCENT_FRAMEWORK_THEMES
                    ) {
                        translucent += info.name
                        changed = true
                        break
                    }
                }
            }
        } while (changed)

        return translucent
    }

    private fun isTranslucentTheme(themeRef: String, translucent: Set<String>): Boolean {
        if (themeRef.contains(TRANSLUCENT)) return true
        val styleName = styleNameFromReference(themeRef) ?: return false
        return styleName in translucent || styleName in KNOWN_TRANSLUCENT_FRAMEWORK_THEMES
    }

    private fun styleNameFromReference(ref: String): String? {
        if (ref.startsWith("?")) return null
        return ref.substringAfterLast("/").removePrefix("@")
    }

    private fun normalizeParent(parent: String?): String? {
        if (parent.isNullOrBlank()) return null
        var p = parent.trim()
        if (p.startsWith("@")) {
            p = p.substringAfterLast("/")
        }
        if (p.startsWith("?") || p.startsWith("@")) return null
        return p
    }

    private fun implicitParent(name: String): String? {
        val lastDot = name.lastIndexOf('.')
        return if (lastDot > 0) name.substring(0, lastDot) else null
    }

    companion object {
        private val NON_FIXED_ORIENTATIONS = setOf(
            "unspecified",
            "behind",
            "user",
            "sensor",
            "fullSensor",
            "reverseFullSensor",
            "fullUser"
        )

        private val KNOWN_TRANSLUCENT_FRAMEWORK_THEMES = setOf(
            "android:Theme.Translucent",
            "android:Theme.Translucent.NoTitleBar",
            "android:Theme.Translucent.NoTitleBar.Fullscreen",
            "android:Theme.Holo.Translucent",
            "android:Theme.Holo.Translucent.NoTitleBar",
            "android:Theme.DeviceDefault.Translucent",
            "android:style/Theme.Translucent"
        )

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.MANIFEST_SCOPE, Scope.RESOURCE_FILE_SCOPE)
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported
                on apps with targetSdkVersion O or greater since there can be another activity
                visible behind your activity with a conflicting request. In this case the system
                can only honor one of the requests and currently prefers to honor the request
                from non-translucent activities. Devices running platform version O or greater
                will throw an IllegalStateException if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }
}