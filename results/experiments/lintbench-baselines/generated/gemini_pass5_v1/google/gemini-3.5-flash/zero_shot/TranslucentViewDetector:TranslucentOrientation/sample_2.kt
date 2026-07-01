package com.android.tools.lint.checks

import com.android.SdkConstants
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

    private val translucentStyles = mutableSetOf<String>()
    private val styleParents = mutableMapOf<String, String>()
    private val activities = mutableListOf<ActivityData>()
    private var applicationTheme: String? = null

    private class ActivityData(
        val element: Element,
        val location: Location,
        val orientation: String,
        val theme: String?
    )

    override fun beforeCheckEachProject(context: Context) {
        translucentStyles.clear()
        styleParents.clear()
        activities.clear()
        applicationTheme = null
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("activity", "application", "style")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "application" -> {
                val theme = element.getAttributeNS(SdkConstants.ANDROID_URI, "theme")
                if (!theme.isNullOrEmpty()) {
                    applicationTheme = theme
                }
            }
            "activity" -> {
                val orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, "screenOrientation")
                if (!orientation.isNullOrEmpty() && FIXED_ORIENTATIONS.contains(orientation)) {
                    val theme = element.getAttributeNS(SdkConstants.ANDROID_URI, "theme")
                    val location = context.getLocation(element)
                    activities.add(ActivityData(element, location, orientation, theme))
                }
            }
            "style" -> {
                val styleName = element.getAttribute("name")
                if (styleName.isNotEmpty()) {
                    val parent = element.getAttribute("parent")
                    if (parent.isNotEmpty()) {
                        styleParents[styleName] = parent
                    } else if (styleName.contains('.')) {
                        val parentName = styleName.substringBeforeLast('.')
                        styleParents[styleName] = parentName
                    }

                    var isTranslucent = false
                    val childNodes = element.childNodes
                    for (i in 0 until childNodes.length) {
                        val child = childNodes.item(i)
                        if (child is Element && child.tagName == "item") {
                            val name = child.getAttribute("name")
                            val value = child.textContent?.trim()
                            if ((name == "android:windowIsTranslucent" ||
                                 name == "android:windowSwipeToDismiss" ||
                                 name == "android:windowIsFloating" ||
                                 name == "windowIsTranslucent" ||
                                 name == "windowSwipeToDismiss" ||
                                 name == "windowIsFloating") && value == "true") {
                                isTranslucent = true
                                break
                            }
                        }
                    }
                    if (isTranslucent || isInherentlyTranslucent(styleName)) {
                        translucentStyles.add(styleName)
                    }
                }
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk in 1..25) {
            return
        }

        for (activity in activities) {
            val theme = activity.theme ?: applicationTheme
            if (theme != null) {
                if (isStyleTranslucent(theme)) {
                    context.report(
                        ISSUE,
                        activity.location,
                        "Only fullscreen opaque activities can request orientation"
                    )
                }
            }
        }
    }

    private fun isStyleTranslucent(styleName: String, visited: MutableSet<String> = mutableSetOf()): Boolean {
        val cleanName = styleName.substringAfter('/')
        if (!visited.add(cleanName)) {
            return false
        }
        if (translucentStyles.contains(cleanName) || isInherentlyTranslucent(cleanName)) {
            return true
        }
        val parent = styleParents[cleanName]
        if (parent != null) {
            return isStyleTranslucent(parent, visited)
        }
        return false
    }

    private fun isInherentlyTranslucent(styleName: String): Boolean {
        val name = styleName.substringAfter('/')
        return name.contains("Translucent", ignoreCase = true) ||
               name.contains("Dialog", ignoreCase = true) ||
               name.contains("Floating", ignoreCase = true)
    }

    companion object {
        private val FIXED_ORIENTATIONS = setOf(
            "landscape",
            "portrait",
            "userLandscape",
            "userPortrait",
            "sensorLandscape",
            "sensorPortrait",
            "reverseLandscape",
            "reversePortrait",
            "locked"
        )

        private val IMPLEMENTATION = Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with targetSdkVersion O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind \
                your translucent activity request portrait. In this case the system can only \
                honor one of the requests and currently prefers to honor the request from \
                non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }
}