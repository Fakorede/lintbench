package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), Detector.XmlScanner {

    companion object {
        private const val ANDROID_O = 26

        private val FIXED_ORIENTATIONS = setOf(
            "landscape",
            "portrait",
            "reverseLandscape",
            "reversePortrait",
            "sensorLandscape",
            "sensorPortrait",
            "locked"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported on apps with
                `targetSdkVersion` O or greater since there can be another activity visible behind your activity with a
                conflicting request.

                Devices running platform version O or greater will throw an exception in your app if this state is
                detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST_SCOPE, Scope.RESOURCE_FILE_SCOPE)
            )
        )
    }

    private data class StyleDef(
        val name: String,
        val parent: String?,
        val translucent: Boolean
    )

    private data class ActivityInfo(
        val location: Location,
        val orientation: String,
        val theme: String
    )

    private val styles = mutableMapOf<String, StyleDef>()
    private val activities = mutableListOf<ActivityInfo>()

    override fun getApplicableFiles(): Collection<Scope> =
        EnumSet.of(Scope.MANIFEST_SCOPE, Scope.RESOURCE_FILE_SCOPE)

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_STYLE, SdkConstants.TAG_ACTIVITY)

    override fun beforeCheckRootProject(context: Context) {
        styles.clear()
        activities.clear()
    }

    override fun afterCheckRootProject(context: Context) {
        for (activity in activities) {
            if (isTranslucentTheme(activity.theme)) {
                val incident = Incident(
                    context,
                    ISSUE,
                    activity.location,
                    "Combining a fixed screenOrientation with a translucent theme is not supported when targeting O or higher"
                )
                context.report(incident)
            }
        }
        styles.clear()
        activities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            SdkConstants.TAG_STYLE -> collectStyle(element)
            SdkConstants.TAG_ACTIVITY -> collectActivity(context, element)
        }
    }

    private fun collectStyle(element: Element) {
        val name = element.getAttribute(SdkConstants.ATTR_NAME)
        if (name.isBlank()) return

        val parentAttr = element.getAttribute(SdkConstants.ATTR_PARENT)
        val parent = resolveParent(name, parentAttr)
        val translucent = hasWindowIsTranslucent(element)

        styles[name] = StyleDef(name, parent, translucent)
    }

    private fun resolveParent(name: String, parentAttr: String): String? {
        if (parentAttr.isNotBlank()) {
            return parentAttr
                .removePrefix("@style/")
                .removePrefix("@android:style/")
                .removePrefix("@*android:style/")
                .removePrefix("@")
        }

        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else null
    }

    private fun hasWindowIsTranslucent(style: Element): Boolean {
        val items = style.getElementsByTagName(SdkConstants.TAG_ITEM)
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            val attrName = item.getAttribute(SdkConstants.ATTR_NAME)
            val localName = when {
                attrName.startsWith("android:") -> attrName.substringAfter("android:")
                else -> attrName
            }
            if (localName == "windowIsTranslucent" && item.textContent.trim() == "true") {
                return true
            }
        }
        return false
    }

    private fun collectActivity(context: XmlContext, element: Element) {
        if (context.project.targetSdk < ANDROID_O) return

        val orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SCREEN_ORIENTATION)
        if (orientation.isBlank() || orientation == "unspecified") return
        if (orientation !in FIXED_ORIENTATIONS) return

        var theme = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
        if (theme.isBlank()) {
            val manifest = context.document.documentElement
            val apps = manifest.getElementsByTagName(SdkConstants.TAG_APPLICATION)
            if (apps.length > 0) {
                val app = apps.item(0) as Element
                theme = app.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME)
            }
        }
        if (theme.isBlank()) return

        activities.add(
            ActivityInfo(
                location = context.getElementNameLocation(element),
                orientation = orientation,
                theme = theme
            )
        )
    }

    private fun isTranslucentTheme(theme: String): Boolean {
        if (theme.contains("Translucent", ignoreCase = true)) return true

        val styleName = when {
            theme.startsWith("@style/") -> theme.substringAfter("@style/")
            theme.startsWith("@android:style/") -> "android:" + theme.substringAfter("@android:style/")
            theme.startsWith("@*android:style/") -> "android:" + theme.substringAfterLast('/')
            else -> theme
        }

        return isStyleTranslucent(styleName)
    }

    private fun isStyleTranslucent(name: String): Boolean {
        val visited = mutableSetOf<String>()
        var current = name
        while (current.isNotBlank() && current !in visited) {
            visited.add(current)
            val style = styles[current]
            if (style != null) {
                if (style.translucent) return true
                current = style.parent ?: break
            } else {
                if (current.contains("Translucent", ignoreCase = true)) return true
                break
            }
        }
        return false
    }
}