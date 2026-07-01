package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), XmlScanner {

    private var applicationTheme: String? = null
    private val activities = mutableListOf<ActivityData>()
    private val styles = mutableMapOf<String, StyleData>()

    class ActivityData(
        val name: String,
        val theme: String?,
        val orientation: String,
        val location: Location
    )

    class StyleData(
        val name: String,
        val parent: String?,
        var declaredTranslucent: Boolean
    )

    override fun getApplicableElements(): Collection<String> {
        return listOf("activity", "application", "style")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "application" -> {
                val theme = element.getAttributeNS(SdkConstants.ANDROID_URI, "theme")
                if (theme.isNotEmpty()) {
                    applicationTheme = theme
                }
            }
            "activity" -> {
                val orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, "screenOrientation")
                if (orientation.isNotEmpty() && FIXED_ORIENTATIONS.contains(orientation)) {
                    val theme = element.getAttributeNS(SdkConstants.ANDROID_URI, "theme").takeIf { it.isNotEmpty() }
                    val name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name")
                    val location = context.getNameLocation(element)
                    activities.add(ActivityData(name, theme, orientation, location))
                }
            }
            "style" -> {
                val name = element.getAttribute("name")
                if (name.isNotEmpty()) {
                    val parentAttr = element.getAttribute("parent").takeIf { it.isNotEmpty() }
                    val parent = parentAttr ?: if (name.contains('.')) name.substringBeforeLast('.') else null
                    
                    var declaredTranslucent = false
                    val childNodes = element.childNodes
                    for (i in 0 until childNodes.length) {
                        val node = childNodes.item(i)
                        if (node is Element && node.tagName == "item") {
                            val itemName = node.getAttribute("name")
                            val itemValue = node.textContent?.trim()
                            if ((itemName == "android:windowIsTranslucent" || itemName == "windowIsTranslucent" ||
                                 itemName == "android:windowIsFloating" || itemName == "windowIsFloating" ||
                                 itemName == "android:windowSwipeToDismiss" || itemName == "windowSwipeToDismiss") &&
                                itemValue == "true") {
                                declaredTranslucent = true
                            }
                        }
                    }
                    styles[name] = StyleData(name, parent, declaredTranslucent)
                }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        try {
            if (context.project.targetSdkVersion.apiLevel < 26) {
                return
            }

            for (activity in activities) {
                val theme = activity.theme ?: applicationTheme ?: continue
                if (isTranslucent(theme, mutableSetOf())) {
                    context.report(
                        ISSUE,
                        activity.location,
                        "Should not specify `screenOrientation` with a translucent theme"
                    )
                }
            }
        } finally {
            activities.clear()
            styles.clear()
            applicationTheme = null
        }
    }

    private fun isTranslucent(themeName: String, visited: MutableSet<String>): Boolean {
        val cleanName = themeName.substringAfter('/')
        if (isKnownTranslucentName(cleanName)) return true
        if (!visited.add(cleanName)) return false
        val style = styles[cleanName] ?: return false
        if (style.declaredTranslucent) return true
        val parent = style.parent
        if (parent != null) {
            return isTranslucent(parent, visited)
        }
        return false
    }

    private fun isKnownTranslucentName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("translucent") ||
               lower.contains("dialog") ||
               lower.contains("floating") ||
               lower.contains("nodisplay") ||
               lower.contains("transparent")
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
                on apps with `targetSdkVersion` O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.
                
                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_AND_RESOURCE_SCOPE
            )
        )
    }
}