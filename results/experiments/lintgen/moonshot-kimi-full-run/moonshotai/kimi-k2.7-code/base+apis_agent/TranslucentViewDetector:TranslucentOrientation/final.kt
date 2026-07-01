package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
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
import org.w3c.dom.Node
import java.util.EnumSet

class TranslucentViewDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screenOrientation with a translucent theme isn't supported on apps with `targetSdkVersion` O or greater since there can be another activity visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind your translucent activity requests portrait. In this case the system can only honor one of the requests and currently prefers to honor the request from non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your app if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST_SCOPE, Scope.RESOURCE_FILE_SCOPE)
            )
        )

        private const val ANDROID_O = 26
        private const val WINDOW_IS_TRANSLUCENT = "android:windowIsTranslucent"
    }

    private val styles = mutableMapOf<String, Style>()
    private val activities = mutableListOf<Activity>()
    private var applicationTheme: String? = null

    private class Style(val parent: String?, val translucent: Boolean)
    private class Activity(val location: Location, val orientation: String, val theme: String?)

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_ACTIVITY, SdkConstants.TAG_APPLICATION, SdkConstants.TAG_STYLE)

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun beforeCheckEachProject(context: Context) {
        styles.clear()
        activities.clear()
        applicationTheme = null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            SdkConstants.TAG_STYLE -> {
                val name = element.getAttribute(SdkConstants.ATTR_NAME)
                if (name.isEmpty()) return

                var parent = element.getAttribute(SdkConstants.ATTR_PARENT)
                if (parent.isEmpty()) {
                    val dot = name.lastIndexOf('.')
                    if (dot > 0) {
                        parent = name.substring(0, dot)
                    }
                }

                var translucent = false
                var child = element.firstChild
                while (child != null) {
                    if (child.nodeType == Node.ELEMENT_NODE && child is Element && child.tagName == SdkConstants.TAG_ITEM) {
                        val attrName = child.getAttribute(SdkConstants.ATTR_NAME)
                        if (attrName == WINDOW_IS_TRANSLUCENT || attrName == "windowIsTranslucent") {
                            val value = child.textContent?.trim()
                            if (value == "true") {
                                translucent = true
                                break
                            }
                        }
                    }
                    child = child.nextSibling
                }

                styles[name] = Style(parent.takeIf { it.isNotEmpty() }, translucent)
            }
            SdkConstants.TAG_ACTIVITY -> {
                val orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SCREEN_ORIENTATION)
                if (orientation.isEmpty()) return

                val theme = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME).takeIf { it.isNotEmpty() }
                activities.add(Activity(context.getLocation(element), orientation, theme))
            }
            SdkConstants.TAG_APPLICATION -> {
                applicationTheme = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_THEME).takeIf { it.isNotEmpty() }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val targetSdk = context.project.targetSdkVersion?.featureLevel ?: 0
        if (targetSdk < ANDROID_O) return

        for (activity in activities) {
            val orientation = activity.orientation
            if (orientation == "unspecified" || orientation == "behind") continue

            val theme = activity.theme ?: applicationTheme ?: continue
            if (isTranslucent(theme)) {
                context.report(
                    ISSUE,
                    activity.location,
                    "Should not specify a fixed screenOrientation with a translucent theme"
                )
            }
        }
    }

    private fun isTranslucent(theme: String): Boolean = isTranslucent(theme, HashSet())

    private fun isTranslucent(theme: String, seen: MutableSet<String>): Boolean {
        return when {
            theme.startsWith("@style/") -> isTranslucent(theme.substring("@style/".length), seen)
            theme.startsWith("@android:style/") -> {
                if (theme.contains("Translucent")) return true
                isTranslucent(theme.substring("@android:style/".length), seen)
            }
            theme.startsWith("style/") -> isTranslucent(theme.substring("style/".length), seen)
            theme.startsWith("android:style/") -> {
                if (theme.contains("Translucent")) return true
                isTranslucent(theme.substring("android:style/".length), seen)
            }
            theme.startsWith("@") -> false
            else -> {
                if (!seen.add(theme)) return false
                val style = styles[theme] ?: return false
                if (style.translucent) return true
                style.parent?.let { isTranslucent(it, seen) } ?: false
            }
        }
    }
}