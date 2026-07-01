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

    private val translucentStyles = mutableSetOf<String>()
    private val fixedOrientationActivities = mutableListOf<ActivityInfo>()
    private var applicationTheme: String? = null

    private data class ActivityInfo(
        val theme: String?,
        val orientation: String,
        val location: Location
    )

    override fun beforeCheckEachProject(context: Context) {
        translucentStyles.clear()
        fixedOrientationActivities.clear()
        applicationTheme = null
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("activity", "application", "style")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "style" -> {
                val name = element.getAttribute("name") ?: return
                val parent = element.getAttribute("parent") ?: ""
                var isTranslucent = name.contains("Translucent", ignoreCase = true) ||
                        name.contains("Dialog", ignoreCase = true) ||
                        name.contains("Floating", ignoreCase = true) ||
                        parent.contains("Translucent", ignoreCase = true) ||
                        parent.contains("Dialog", ignoreCase = true) ||
                        parent.contains("Floating", ignoreCase = true)

                if (!isTranslucent) {
                    val childNodes = element.childNodes
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
                                break
                            }
                        }
                    }
                }
                if (isTranslucent) {
                    translucentStyles.add(normalizeStyleName(name))
                }
            }
            "activity" -> {
                val orientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "screenOrientation").takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("android:screenOrientation")
                if (isFixedOrientation(orientation)) {
                    val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme").takeIf { it.isNotEmpty() }
                        ?: element.getAttribute("android:theme")
                    val location = context.getLocation(element)
                    fixedOrientationActivities.add(ActivityInfo(theme.takeIf { it.isNotEmpty() }, orientation, location))
                }
            }
            "application" -> {
                val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme").takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("android:theme")
                if (!theme.isNullOrEmpty()) {
                    applicationTheme = theme
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk < 26) return

        for (activity in fixedOrientationActivities) {
            val theme = activity.theme ?: applicationTheme
            if (theme != null && isThemeTranslucent(theme)) {
                context.report(
                    ISSUE,
                    activity.location,
                    "Only fullscreen opaque activities can request orientation"
                )
            }
        }
    }

    private fun isFixedOrientation(orientation: String?): Boolean {
        return orientation in FIXED_ORIENTATIONS
    }

    private fun normalizeStyleName(theme: String): String {
        return theme.substringAfterLast("/")
    }

    private fun isThemeTranslucent(theme: String): Boolean {
        val normalized = normalizeStyleName(theme)
        if (normalized.contains("Translucent", ignoreCase = true) ||
            normalized.contains("Dialog", ignoreCase = true) ||
            normalized.contains("Floating", ignoreCase = true)) {
            return true
        }
        return translucentStyles.contains(normalized)
    }

    companion object {
        private val FIXED_ORIENTATIONS = setOf(
            "portrait",
            "landscape",
            "reversePortrait",
            "reverseLandscape",
            "sensorPortrait",
            "sensorLandscape",
            "userPortrait",
            "userLandscape",
            "locked"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be another activity \
                visible behind your activity with a conflicting request.
                
                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
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