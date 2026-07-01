package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ATTR_ICON
import com.android.SdkConstants.ATTR_ROUND_ICON
import com.android.SdkConstants.TAG_APPLICATION
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
import java.io.File

class MonochromeLauncherIconDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to tint \
                app icons. Providing a `<monochrome>` layer (which will be used for tinting) for \
                every adaptive icon is strongly recommended, otherwise Android 16 QPR 2 and above \
                will simply tint the color version of the icon, which may look unusual. Devices \
                running earlier Android versions will (with no monochrome layer) show the untinted \
                color icon for your app, which will look inconsistent.
            """,
            category = Category.ICONS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                MonochromeLauncherIconDetector::class.java,
                Scope.MANIFEST_AND_RESOURCE_SCOPE,
                Scope.MANIFEST_SCOPE
            )
        )

        private const val TAG_ADAPTIVE_ICON = "adaptive-icon"
        private const val TAG_MONOCHROME = "monochrome"
    }

    /** Icon resource names extracted from the manifest (without @mipmap/ or @drawable/ prefix). */
    private val launcherIconNames = mutableSetOf<String>()

    /** Location of the <application> element in the manifest for fallback reporting. */
    private var manifestLocation: Location? = null

    /**
     * Adaptive icon files that are missing a <monochrome> child, mapped
     * resource-name → Location.
     */
    private val adaptiveIconsMissingMonochrome = mutableMapOf<String, Location>()

    /**
     * Adaptive icon files that DO have a <monochrome> child.
     */
    private val adaptiveIconsWithMonochrome = mutableSetOf<String>()

    // -------------------------------------------------------------------------
    // XmlScanner
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_APPLICATION, TAG_ADAPTIVE_ICON)

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_APPLICATION -> visitApplicationElement(context, element)
            TAG_ADAPTIVE_ICON -> visitAdaptiveIconElement(context, element)
        }
    }

    private fun visitApplicationElement(context: XmlContext, element: Element) {
        manifestLocation = context.getLocation(element)

        fun extractName(attrValue: String?): String? {
            if (attrValue.isNullOrEmpty()) return null
            // e.g. "@mipmap/ic_launcher" or "@drawable/ic_launcher"
            val slash = attrValue.lastIndexOf('/')
            return if (slash >= 0) attrValue.substring(slash + 1) else attrValue
        }

        val icon = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android", ATTR_ICON
        ).ifEmpty {
            element.getAttribute(ATTR_ICON)
        }
        val roundIcon = element.getAttributeNS(
            "http://schemas.android.com/apk/res/android", ATTR_ROUND_ICON
        ).ifEmpty {
            element.getAttribute(ATTR_ROUND_ICON)
        }

        extractName(icon)?.let { launcherIconNames.add(it) }
        extractName(roundIcon)?.let { launcherIconNames.add(it) }
    }

    private fun visitAdaptiveIconElement(context: XmlContext, element: Element) {
        val file = context.file
        val resourceName = file.nameWithoutExtension

        val hasMonochrome = hasMonochromeChild(element)

        if (hasMonochrome) {
            adaptiveIconsWithMonochrome.add(resourceName)
            // Remove any previously recorded missing entry (e.g. from another density bucket)
            adaptiveIconsMissingMonochrome.remove(resourceName)
        } else {
            // Only record as missing if we haven't already seen a version WITH monochrome
            if (resourceName !in adaptiveIconsWithMonochrome) {
                adaptiveIconsMissingMonochrome[resourceName] = context.getLocation(element)
            }
        }
    }

    private fun hasMonochromeChild(adaptiveIcon: Element): Boolean {
        val children = adaptiveIcon.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == TAG_MONOCHROME) {
                return true
            }
        }
        return false
    }

    // -------------------------------------------------------------------------
    // Project-level hook – report after everything has been scanned
    // -------------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        // For each launcher icon name, check whether we found an adaptive icon
        // for it that is missing a monochrome layer.
        for (iconName in launcherIconNames) {
            if (iconName in adaptiveIconsWithMonochrome) {
                // At least one density bucket has monochrome – fine.
                continue
            }
            val location = adaptiveIconsMissingMonochrome[iconName]
                ?: manifestLocation
                ?: continue

            context.report(
                issue = ISSUE,
                location = location,
                message = "The launcher icon `$iconName` does not have a `<monochrome>` tag"
            )
        }
    }
}