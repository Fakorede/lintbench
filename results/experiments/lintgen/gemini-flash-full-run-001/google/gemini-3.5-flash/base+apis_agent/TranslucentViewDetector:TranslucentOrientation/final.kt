package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("activity", "application", "style")
    }

    override fun beforeCheckEachProject(context: Context) {
        activities.clear()
        styles.clear()
        applicationTheme = null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "application" -> {
                val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme").takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("android:theme").takeIf { it.isNotEmpty() }
                if (theme != null) {
                    applicationTheme = theme
                }
            }
            "activity" -> {
                val orientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "screenOrientation").takeIf { it.isNotEmpty() }
                    ?: element.getAttribute("android:screenOrientation").takeIf { it.isNotEmpty() }
                if (orientation != null && FIXED_ORIENTATIONS.contains(orientation)) {
                    val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme").takeIf { it.isNotEmpty() }
                        ?: element.getAttribute("android:theme").takeIf { it.isNotEmpty() }
                    val name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name").takeIf { it.isNotEmpty() }
                        ?: element.getAttribute("android:name").takeIf { it.isNotEmpty() } ?: ""
                    val location = context.getLocation(element)
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
                            val itemValue = node.textContent?.trim()?.lowercase()
                            if ((itemName == "android:windowIsTranslucent" || itemName == "windowIsTranslucent" ||
                                 itemName == "android:windowIsFloating" || itemName == "windowIsFloating" ||
                                 itemName == "android:windowSwipeToDismiss" || itemName == "windowSwipeToDismiss") &&
                                (itemValue == "true" || itemValue == "1" || itemValue == "@bool/true" || itemValue == "@android:bool/true")) {
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
        val targetSdk = context.project.targetSdkVersion.apiLevel
        if (targetSdk in 2..25) {
            activities.clear()
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
        activities.clear()
    }

    private fun isTranslucent(themeName: String, visited: MutableSet<String>): Boolean {
        val cleanName = cleanThemeName(themeName)
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

    private fun cleanThemeName(themeName: String): String {
        var name = themeName
        if (name.startsWith("@")) {
            name = name.substring(1)
        }
        if (name.startsWith("android:")) {
            name = name.substring("android:".length)
        }
        if (name.startsWith("style/")) {
            name = name.substring("style/".length)
        }
        return name
    }

    private fun isKnownTranslucentName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("translucent") ||
               lower.contains("dialog") ||
               lower.contains("floating") ||
               lower.contains("nodisplay") ||
               lower.contains("transparent") ||
               lower.contains("wallpaper")
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
                Specifying a fixed screen orientation with a translucent theme isn't supported on apps with `targetSdkVersion` O or greater since there can be an another activity visible behind your activity with a conflicting request.
                
                Devices running platform version O or greater will throw an exception in your app if this state is detected.
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