package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_TARGET_SDK_VERSION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.SdkConstants.TAG_USES_SDK
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

class TranslucentViewDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(
        TAG_USES_SDK,
        TAG_APPLICATION,
        TAG_ACTIVITY,
        TAG_STYLE
    )

    override fun beforeCheckRootProject(context: Context) {
        val token = context.client
        val iterator = STATES.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.first !== token) {
                iterator.remove()
            }
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val state = getState(context)

        when (element.tagName) {
            TAG_USES_SDK -> {
                val raw = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
                state.targetSdk = maxOf(state.targetSdk, parseTargetSdk(raw))
            }
            TAG_APPLICATION -> {
                val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                if (theme.isNotBlank()) {
                    state.applicationTheme = normalizeThemeRef(theme)
                }
            }
            TAG_ACTIVITY -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                if (name.isNotBlank() && orientation.isNotBlank()) {
                    val effectiveTheme = if (theme.isNotBlank()) {
                        normalizeThemeRef(theme)
                    } else {
                        state.applicationTheme
                    }
                    state.activities.add(
                        ActivityInfo(
                            name = name,
                            orientation = orientation,
                            themeRef = effectiveTheme,
                            location = context.getLocation(element)
                        )
                    )
                }
            }
            TAG_STYLE -> {
                val styleName = element.getAttribute(ATTR_NAME)
                if (styleName.isNotBlank()) {
                    val parentAttr = element.getAttribute(ATTR_PARENT)
                    val parentRef = when {
                        parentAttr.isNotBlank() -> normalizeThemeRef(parentAttr)
                        else -> {
                            val dot = styleName.lastIndexOf('.')
                            if (dot > 0) "@style/${styleName.substring(0, dot)}" else ""
                        }
                    }

                    var translucent = false
                    val items = element.getElementsByTagName(TAG_ITEM)
                    for (i in 0 until items.length) {
                        val item = items.item(i) as? Element ?: continue
                        val itemName = item.getAttribute(ATTR_NAME)
                        if (itemName == "android:windowIsTranslucent" || itemName == "windowIsTranslucent") {
                            if (item.textContent.trim().equals("true", ignoreCase = true)) {
                                translucent = true
                                break
                            }
                        }
                    }

                    state.styles["@style/$styleName"] = StyleInfo(parentRef, translucent)
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val state = getExistingState(context) ?: return

        val effectiveTargetSdk = maxOf(state.targetSdk, context.project.targetSdk)
        if (effectiveTargetSdk < 26) {
            return
        }

        for (activity in state.activities) {
            if (activity.name in state.reported) {
                continue
            }
            if (!isFixedOrientation(activity.orientation)) {
                continue
            }

            val themeRef = activity.themeRef ?: continue
            if (isTranslucent(themeRef, state)) {
                state.reported.add(activity.name)
                context.report(
                    TRANSLUCENT_ORIENTATION,
                    activity.location,
                    "Activity ${activity.name} specifies a fixed screenOrientation " +
                            "(${activity.orientation}) while using a translucent theme. " +
                            "This is not supported when targetSdkVersion is O or greater."
                )
            }
        }
    }

    private fun getState(context: Context): ProjectState {
        val path = context.project.dir.path
        val token = context.client
        val existing = STATES[path]
        return if (existing != null && existing.first === token) {
            existing.second
        } else {
            val state = ProjectState()
            STATES[path] = token to state
            state
        }
    }

    private fun getExistingState(context: Context): ProjectState? {
        val path = context.project.dir.path
        val entry = STATES[path] ?: return null
        return if (entry.first === context.client) entry.second else null
    }

    private fun isTranslucent(
        themeRef: String,
        state: ProjectState,
        visited: MutableSet<String> = mutableSetOf()
    ): Boolean {
        if (themeRef in visited) return false
        visited.add(themeRef)

        if (themeRef in KNOWN_TRANSLUCENT_THEMES) return true

        val style = state.styles[themeRef] ?: return false
        if (style.translucent) return true

        return if (style.parent.isNotBlank()) {
            isTranslucent(style.parent, state, visited)
        } else {
            false
        }
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return when (orientation) {
            "landscape",
            "portrait",
            "reverseLandscape",
            "reversePortrait",
            "sensorLandscape",
            "sensorPortrait",
            "userLandscape",
            "userPortrait",
            "locked" -> true
            else -> false
        }
    }

    private fun normalizeThemeRef(ref: String): String {
        val trimmed = ref.trim()
        return when {
            trimmed.startsWith("@style/") -> trimmed
            trimmed.startsWith("@*android:style/") -> trimmed
            trimmed.startsWith("@android:style/") -> trimmed
            trimmed.startsWith("android:style/") -> "@android:style/${trimmed.substringAfter("android:style/")}"
            trimmed.startsWith("style/") -> "@style/${trimmed.substringAfter("style/")}"
            trimmed.startsWith("android:") -> "@android:style/${trimmed.substringAfter("android:")}"
            trimmed.startsWith("@") -> trimmed
            trimmed.isEmpty() -> ""
            else -> "@style/$trimmed"
        }
    }

    private fun parseTargetSdk(raw: String): Int {
        return when (raw.uppercase()) {
            "O" -> 26
            "O-MR1" -> 27
            "P" -> 28
            "Q" -> 29
            "R" -> 30
            "S" -> 31
            "T" -> 33
            "U" -> 34
            "V" -> 35
            else -> raw.toIntOrNull() ?: 0
        }
    }

    private class ProjectState {
        var targetSdk: Int = 0
        var applicationTheme: String? = null
        val activities = mutableListOf<ActivityInfo>()
        val styles = mutableMapOf<String, StyleInfo>()
        val reported = mutableSetOf<String>()
    }

    private data class ActivityInfo(
        val name: String,
        val orientation: String,
        val themeRef: String?,
        val location: Location
    )

    private data class StyleInfo(
        val parent: String,
        val translucent: Boolean
    )

    companion object {
        private val STATES = mutableMapOf<String, Pair<Any, ProjectState>>()

        @JvmField
        val TRANSLUCENT_ORIENTATION = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screenOrientation together with a translucent theme is not
                supported on apps with targetSdkVersion O or greater. If the activity visible
                behind the translucent activity requests a different orientation, the system
                cannot honor both requests and will throw an exception on Android O and later.
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

        private val KNOWN_TRANSLUCENT_THEMES = setOf(
            "@android:style/Theme.Translucent",
            "@*android:style/Theme.Translucent",
            "@android:style/Theme.Translucent.NoTitleBar",
            "@*android:style/Theme.Translucent.NoTitleBar",
            "@android:style/Theme.Translucent.NoTitleBar.Fullscreen",
            "@*android:style/Theme.Translucent.NoTitleBar.Fullscreen",
            "@android:style/Theme.Holo.Translucent",
            "@*android:style/Theme.Holo.Translucent",
            "@android:style/Theme.Holo.Light.Translucent",
            "@*android:style/Theme.Holo.Light.Translucent",
            "@android:style/Theme.Material.Translucent",
            "@*android:style/Theme.Material.Translucent",
            "@android:style/Theme.Material.Light.Translucent",
            "@*android:style/Theme.Material.Light.Translucent",
            "@android:style/Theme.DeviceDefault.Translucent",
            "@*android:style/Theme.DeviceDefault.Translucent",
            "@android:style/Theme.DeviceDefault.Light.Translucent",
            "@*android:style/Theme.DeviceDefault.Light.Translucent"
        )
    }
}