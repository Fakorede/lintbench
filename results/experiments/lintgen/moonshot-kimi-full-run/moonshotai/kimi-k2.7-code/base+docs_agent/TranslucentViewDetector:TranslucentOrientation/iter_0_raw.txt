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
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), XmlScanner {

    private var targetSdk: Int = 0
    private var applicationTheme: String? = null
    private val activities = mutableListOf<ActivityInfo>()
    private val styles = mutableMapOf<String, StyleInfo>()

    override fun getApplicableElements(): Collection<String>? = listOf(
        TAG_USES_SDK,
        TAG_APPLICATION,
        TAG_ACTIVITY,
        TAG_STYLE
    )

    override fun beforeCheckEachProject(context: Context) {
        targetSdk = 0
        applicationTheme = null
        activities.clear()
        styles.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_USES_SDK -> {
                targetSdk = element.getAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION).toIntOrNull()
                    ?: element.getAttribute(ATTR_TARGET_SDK_VERSION).toIntOrNull()
                    ?: 0
            }
            TAG_APPLICATION -> {
                val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                if (theme.isNotBlank()) {
                    applicationTheme = normalizeThemeRef(theme)
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
                        applicationTheme
                    }
                    activities.add(
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
                    val parentRef = if (parentAttr.isNotBlank()) {
                        normalizeThemeRef(parentAttr)
                    } else {
                        val dot = styleName.lastIndexOf('.')
                        if (dot > 0) "@style/${styleName.substring(0, dot)}" else ""
                    }

                    var translucent = false
                    val items = element.getElementsByTagName(TAG_ITEM)
                    for (i in 0 until items.length) {
                        val item = items.item(i) as? Element ?: continue
                        val itemName = item.getAttribute(ATTR_NAME)
                        if (itemName == "android:windowIsTranslucent" || itemName == "windowIsTranslucent") {
                            if (item.textContent.trim() == "true") {
                                translucent = true
                                break
                            }
                        }
                    }

                    styles["@style/$styleName"] = StyleInfo(parentRef, translucent)
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (targetSdk < 26) {
            return
        }

        for (activity in activities) {
            if (!isFixedOrientation(activity.orientation)) {
                continue
            }

            val themeRef = activity.themeRef ?: continue
            if (isTranslucent(themeRef)) {
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

    private fun isTranslucent(themeRef: String, visited: MutableSet<String> = mutableSetOf()): Boolean {
        if (themeRef in visited) return false
        visited.add(themeRef)

        if (themeRef in KNOWN_TRANSLUCENT_THEMES) return true

        val style = styles[themeRef] ?: return false
        if (style.translucent) return true

        return if (style.parent.isNotBlank()) {
            isTranslucent(style.parent, visited)
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
            trimmed.startsWith("@android:style/") -> trimmed
            trimmed.startsWith("android:style/") -> "@$trimmed"
            trimmed.startsWith("@") -> trimmed
            trimmed.isEmpty() -> ""
            else -> "@style/$trimmed"
        }
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
        @JvmField
        val TRANSLUCENT_ORIENTATION = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screenOrientation together with a translucent theme is not \
                supported on apps with targetSdkVersion O or greater. If the activity visible \
                behind the translucent activity requests a different orientation, the system \
                cannot honor both requests and will throw an exception on Android O and later.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_AND_RESOURCE_SCOPE
            )
        )

        private val KNOWN_TRANSLUCENT_THEMES = setOf(
            "@android:style/Theme.Translucent",
            "@android:style/Theme.Translucent.NoTitleBar",
            "@android:style/Theme.Translucent.NoTitleBar.Fullscreen",
            "@android:style/Theme.Holo.Translucent",
            "@android:style/Theme.Holo.Light.Translucent",
            "@android:style/Theme.Material.Translucent",
            "@android:style/Theme.Material.Light.Translucent",
            "@android:style/Theme.DeviceDefault.Translucent",
            "@android:style/Theme.DeviceDefault.Light.Translucent"
        )
    }
}