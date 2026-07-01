package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

class TranslucentViewDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be an another activity \
                visible behind your activity with a conflicting request.

                For example, your activity requests landscape and the visible activity behind \
                your translucent activity request portrait. In this case the system can only \
                honor one of the requests and currently prefers to honor the request from \
                non-translucent activities since there is nothing visible behind them.

                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )

        private const val ATTR_SCREEN_ORIENTATION = "screenOrientation"
        private const val ATTR_THEME = "theme"

        // Orientation values that are "fixed" (not sensor/unspecified/etc.)
        private val FIXED_ORIENTATIONS = setOf(
            "landscape",
            "portrait",
            "reverseLandscape",
            "reversePortrait",
            "sensorLandscape",
            "sensorPortrait",
            "userLandscape",
            "userPortrait",
            "locked"
        )

        private fun isTranslucentTheme(theme: String): Boolean {
            val lower = theme.lowercase()
            return lower.contains("translucent") ||
                lower.contains("dialog") ||
                lower.contains("floating")
        }
    }

    // Application-level theme (per-project)
    private var applicationTheme: String? = null

    // Map from style name -> whether it sets windowIsTranslucent or windowIsFloating
    private val translucentStyles = mutableSetOf<String>()

    // Pending activity checks: list of (context, element, orientation, orientationAttrNode, theme)
    private data class PendingActivity(
        val context: XmlContext,
        val element: Element,
        val orientation: String,
        val theme: String
    )

    private val pendingActivities = mutableListOf<PendingActivity>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_ACTIVITY, TAG_APPLICATION, "style")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_APPLICATION -> {
                val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                if (theme.isNotEmpty()) {
                    applicationTheme = theme
                }
            }
            TAG_ACTIVITY -> {
                val orientationAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                    ?: return
                val orientation = orientationAttr.value
                if (orientation.isEmpty() || orientation !in FIXED_ORIENTATIONS) return

                val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME).ifEmpty {
                    applicationTheme ?: return
                }

                // Check immediately if obviously translucent
                if (isTranslucentTheme(theme)) {
                    val location = context.getValueLocation(orientationAttr)
                    context.report(
                        ISSUE,
                        element,
                        location,
                        "Combining a fixed orientation (currently `$orientation`) with a " +
                            "translucent theme (`$theme`) is not allowed, and can cause a " +
                            "crash on devices running API 26 and higher"
                    )
                } else {
                    // Defer: theme might be defined in resource files
                    pendingActivities.add(PendingActivity(context, element, orientation, theme))
                }
            }
            "style" -> {
                visitStyle(element)
            }
        }
    }

    private fun visitStyle(element: Element) {
        val styleName = element.getAttribute(ATTR_NAME)
        if (styleName.isEmpty()) return

        // Check parent attribute
        val parent = element.getAttribute("parent")
        if (parent.isNotEmpty() && isTranslucentTheme(parent)) {
            translucentStyles.add(styleName)
            translucentStyles.add("@style/$styleName")
            return
        }

        // Check item children
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName != "item") continue
            val itemName = child.getAttribute(ATTR_NAME)
            val itemValue = child.textContent?.trim() ?: ""
            if ((itemName == "android:windowIsTranslucent" || itemName == "windowIsTranslucent" ||
                        itemName == "android:windowIsFloating" || itemName == "windowIsFloating") &&
                itemValue == "true"
            ) {
                translucentStyles.add(styleName)
                translucentStyles.add("@style/$styleName")
                break
            }
        }
    }

    private fun isThemeTranslucent(theme: String): Boolean {
        if (isTranslucentTheme(theme)) return true
        if (translucentStyles.contains(theme)) return true
        // Try stripping prefix
        val stripped = theme.removePrefix("@style/").removePrefix("@android:style/")
        return translucentStyles.contains(stripped)
    }

    override fun afterCheckFile(context: com.android.tools.lint.detector.api.Context) {
        if (context !is XmlContext) return

        val iterator = pendingActivities.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            // Only process if this is the same file context or if we now have info
            if (isThemeTranslucent(pending.theme)) {
                val orientationAttr = pending.element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                if (orientationAttr != null) {
                    val location = pending.context.getValueLocation(orientationAttr)
                    pending.context.report(
                        ISSUE,
                        pending.element,
                        location,
                        "Combining a fixed orientation (currently `${pending.orientation}`) with a " +
                            "translucent theme (`${pending.theme}`) is not allowed, and can cause a " +
                            "crash on devices running API 26 and higher"
                    )
                }
                iterator.remove()
            }
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        // Process any remaining pending activities now that all files have been scanned
        for (pending in pendingActivities) {
            if (isThemeTranslucent(pending.theme)) {
                val orientationAttr = pending.element.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                if (orientationAttr != null) {
                    val location = pending.context.getValueLocation(orientationAttr)
                    pending.context.report(
                        ISSUE,
                        pending.element,
                        location,
                        "Combining a fixed orientation (currently `${pending.orientation}`) with a " +
                            "translucent theme (`${pending.theme}`) is not allowed, and can cause a " +
                            "crash on devices running API 26 and higher"
                    )
                }
            }
        }
        pendingActivities.clear()
        applicationTheme = null
        translucentStyles.clear()
    }
}