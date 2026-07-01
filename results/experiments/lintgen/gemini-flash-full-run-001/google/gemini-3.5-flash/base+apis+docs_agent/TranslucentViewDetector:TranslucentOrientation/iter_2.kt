package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), XmlScanner {

    private val translucentStyles = mutableSetOf<String>()
    private val styleParents = mutableMapOf<String, String>()
    private var applicationTheme: String? = null

    override fun getApplicableElements(): Collection<String>? {
        return listOf("style", "activity", "application")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val phase = context.phase
        val tagName = element.tagName

        if (phase == 1) {
            if (tagName == "style") {
                val name = element.getAttribute("name")
                if (name.isNotEmpty()) {
                    var parent = element.getAttribute("parent")
                    if (parent.isEmpty() && name.contains('.')) {
                        parent = name.substringBeforeLast('.')
                    }
                    if (parent.isNotEmpty()) {
                        styleParents[name] = parent
                    }

                    var isTranslucent = false
                    val childNodes = element.childNodes
                    for (i in 0 until childNodes.length) {
                        val child = childNodes.item(i)
                        if (child is Element && child.tagName == "item") {
                            val itemName = child.getAttribute("name")
                            val itemValue = child.textContent?.trim()
                            if ((itemName == "android:windowIsTranslucent" || itemName == "windowIsTranslucent" ||
                                 itemName == "android:windowIsFloating" || itemName == "windowIsFloating" ||
                                 itemName == "android:windowSwipeToDismiss" || itemName == "windowSwipeToDismiss") &&
                                itemValue == "true") {
                                isTranslucent = true
                                break
                            }
                        }
                    }
                    if (isTranslucent) {
                        translucentStyles.add(name)
                    }
                }
            } else if (tagName == "application") {
                val theme = element.getAttributeNS(SdkConstants.ANDROID_URI, "theme")
                if (theme.isNotEmpty()) {
                    applicationTheme = theme
                }
            }
        } else if (phase == 2) {
            val targetSdk = try {
                context.project.targetSdkVersion.apiLevel
            } catch (e: Exception) {
                0
            }
            if (targetSdk < 26) {
                return
            }

            if (tagName == "activity") {
                val orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, "screenOrientation")
                if (orientation.isNotEmpty() && isFixedOrientation(orientation)) {
                    val theme = element.getAttributeNS(SdkConstants.ANDROID_URI, "theme").takeIf { it.isNotEmpty() }
                        ?: applicationTheme
                    if (theme != null && isThemeTranslucent(theme)) {
                        context.report(
                            ISSUE,
                            element,
                            context.getNameLocation(element),
                            "Only fullscreen opaque activities can request orientation"
                        )
                    }
                }
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        if (context.phase == 1) {
            context.driver.requestRepeat(this, Scope.MANIFEST_SCOPE)
        }
    }

    private fun isFixedOrientation(orientation: String): Boolean {
        return when (orientation) {
            "portrait",
            "landscape",
            "reversePortrait",
            "reverseLandscape",
            "sensorPortrait",
            "sensorLandscape",
            "userPortrait",
            "userLandscape",
            "locked" -> true
            else -> false
        }
    }

    private fun isNameTranslucent(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("translucent") ||
               lower.contains("dialog") ||
               lower.contains("floating") ||
               lower.contains("swipetodismiss")
    }

    private fun isThemeTranslucent(themeName: String): Boolean {
        val cleanName = themeName.substringAfter('/')

        if (isNameTranslucent(cleanName)) {
            return true
        }

        if (translucentStyles.contains(cleanName)) {
            return true
        }

        var current: String? = cleanName
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current)) {
            if (isNameTranslucent(current) || translucentStyles.contains(current)) {
                return true
            }
            val parent = styleParents[current]
            if (parent != null) {
                current = parent.substringAfter('/')
            } else {
                val lastDot = current.lastIndexOf('.')
                if (lastDot != -1) {
                    current = current.substring(0, lastDot)
                } else {
                    current = null
                }
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.
                """,
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