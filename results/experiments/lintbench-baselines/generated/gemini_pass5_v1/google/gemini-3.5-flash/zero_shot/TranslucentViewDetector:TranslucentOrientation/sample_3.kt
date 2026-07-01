package com.android.tools.lint.checks

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

class TranslucentViewDetector : Detector(), XmlScanner {

    private val translucentStyles = mutableSetOf<String>()
    private val styleParents = mutableMapOf<String, String>()
    private val activitiesToCheck = mutableListOf<ActivityInfo>()
    private var applicationTheme: String? = null

    class ActivityInfo(
        val element: Element,
        val context: XmlContext,
        val theme: String?,
        val orientation: String,
        val location: Location
    )

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("activity", "application", "style")
    }

    override fun beforeCheckProject(context: Context) {
        translucentStyles.clear()
        styleParents.clear()
        activitiesToCheck.clear()
        applicationTheme = null

        val standardTranslucent = listOf(
            "Theme.Translucent",
            "Theme.Translucent.NoTitleBar",
            "Theme.Translucent.NoTitleBar.Fullscreen",
            "Theme.Dialog",
            "Theme.Dialog.Alert",
            "Theme.Dialog.NoActionBar",
            "Theme.Dialog.MinWidth",
            "Theme.Wallpaper",
            "Theme.Wallpaper.NoTitleBar",
            "Theme.Holo.Dialog",
            "Theme.Holo.Dialog.Alert",
            "Theme.Holo.Dialog.NoActionBar",
            "Theme.Holo.Dialog.MinWidth",
            "Theme.Holo.Wallpaper",
            "Theme.Holo.Wallpaper.NoTitleBar",
            "Theme.Material.Dialog",
            "Theme.Material.Dialog.Alert",
            "Theme.Material.Dialog.NoActionBar",
            "Theme.Material.Dialog.MinWidth",
            "Theme.Material.Wallpaper",
            "Theme.Material.Wallpaper.NoTitleBar",
            "Theme.DeviceDefault.Dialog",
            "Theme.DeviceDefault.Dialog.Alert",
            "Theme.DeviceDefault.Dialog.NoActionBar",
            "Theme.DeviceDefault.Dialog.MinWidth",
            "Theme.DeviceDefault.Wallpaper",
            "Theme.DeviceDefault.Wallpaper.NoTitleBar",
            "Theme.AppCompat.Dialog",
            "Theme.AppCompat.Dialog.Alert",
            "Theme.AppCompat.Dialog.MinWidth",
            "Theme.AppCompat.Light.Dialog",
            "Theme.AppCompat.Light.Dialog.Alert",
            "Theme.AppCompat.Light.Dialog.MinWidth",
            "Theme.Design.BottomSheetDialog",
            "Theme.Design.Light.BottomSheetDialog",
            "Theme.MaterialComponents.Dialog",
            "Theme.MaterialComponents.Dialog.Alert",
            "Theme.MaterialComponents.Dialog.MinWidth",
            "Theme.MaterialComponents.BottomSheetDialog",
            "Theme.Material3.Dialog",
            "Theme.Material3.Dialog.Alert",
            "Theme.Material3.Dialog.MinWidth",
            "Theme.Material3.BottomSheetDialog"
        )
        for (theme in standardTranslucent) {
            translucentStyles.add(theme)
            translucentStyles.add(theme.lowercase())
        }
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "application" -> {
                val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                if (theme.isNotEmpty()) {
                    applicationTheme = theme
                }
            }
            "activity" -> {
                val orientation = element.getAttributeNS("http://schemas.android.com/apk/res/android", "screenOrientation")
                if (orientation.isNotEmpty() && isFixedOrientation(orientation)) {
                    val theme = element.getAttributeNS("http://schemas.android.com/apk/res/android", "theme")
                        .takeIf { it.isNotEmpty() }
                    activitiesToCheck.add(
                        ActivityInfo(
                            element = element,
                            context = context,
                            theme = theme,
                            orientation = orientation,
                            location = context.getNameLocation(element)
                        )
                    )
                }
            }
            "style" -> {
                val name = element.getAttribute("name")
                if (name.isNotEmpty()) {
                    val parent = element.getAttribute("parent").takeIf { it.isNotEmpty() }
                        ?: if (name.contains('.')) name.substringBeforeLast('.') else null

                    if (parent != null) {
                        styleParents[cleanStyleName(name)] = cleanStyleName(parent)
                    }

                    val childNodes = element.childNodes
                    for (i in 0 until childNodes.length) {
                        val child = childNodes.item(i)
                        if (child is Element && child.tagName == "item") {
                            val itemName = child.getAttribute("name")
                            val cleanItemName = itemName.substringAfter("android:")
                            if (cleanItemName == "windowIsTranslucent" ||
                                cleanItemName == "windowIsFloating" ||
                                cleanItemName == "windowSwipeToDismiss"
                            ) {
                                val value = child.textContent?.trim()
                                if (value == "true") {
                                    translucentStyles.add(cleanStyleName(name))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        var changed = true
        var iterations = 0
        while (changed && iterations < 100) {
            changed = false
            iterations++
            for ((style, parent) in styleParents) {
                if (style !in translucentStyles) {
                    if (isStyleTranslucent(parent)) {
                        translucentStyles.add(style)
                        changed = true
                    }
                }
            }
        }

        for (activity in activitiesToCheck) {
            val targetSdk = activity.context.project.targetSdkVersion.apiLevel
            if (targetSdk < 26) {
                continue
            }

            val themeName = activity.theme ?: applicationTheme
            if (themeName != null) {
                if (isStyleTranslucent(cleanStyleName(themeName))) {
                    val message = "Only fullscreen opaque activities can request orientation"
                    activity.context.report(
                        ISSUE,
                        activity.element,
                        activity.location,
                        message
                    )
                }
            }
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

    private fun cleanStyleName(name: String): String {
        return name
            .removePrefix("@style/")
            .removePrefix("@android:style/")
            .removePrefix("style/")
            .trim()
    }

    private fun isStyleTranslucent(styleName: String): Boolean {
        val clean = cleanStyleName(styleName)
        if (clean in translucentStyles || clean.lowercase() in translucentStyles) {
            return true
        }
        val lower = clean.lowercase()
        return lower.contains("translucent") || lower.contains("dialog") || lower.contains("floating")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Activity is using a translucent theme and a fixed orientation",
            explanation = """
                Specifying a fixed screen orientation with a translucent theme isn't supported \
                on apps with `targetSdkVersion` O or greater since there can be another activity \
                visible behind your activity with a conflicting request.

                Devices running platform version O or greater will throw an exception in your \
                app if this state is detected.
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