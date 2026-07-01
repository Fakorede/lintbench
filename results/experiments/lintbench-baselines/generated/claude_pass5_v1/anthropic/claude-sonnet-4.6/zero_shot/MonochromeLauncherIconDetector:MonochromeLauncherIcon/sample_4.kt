/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ATTR_ICON
import com.android.SdkConstants.ATTR_ROUND_ICON
import com.android.SdkConstants.TAG_APPLICATION
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.util.EnumSet

/**
 * Checks that adaptive launcher icons include a `<monochrome>` layer.
 */
class MonochromeLauncherIconDetector : ResourceXmlDetector(), XmlScanner {

    /**
     * Set of adaptive icon resource names (without @drawable/ prefix) that DO have a monochrome
     * layer defined.
     */
    private val iconsWithMonochrome = mutableSetOf<String>()

    /**
     * Map from adaptive icon resource name to the XmlContext + Element where it was declared
     * without a monochrome layer, so we can report it after scanning all files.
     */
    private val iconsWithoutMonochrome = mutableMapOf<String, Pair<XmlContext, Element>>()

    /**
     * The set of icon resource names referenced from the manifest's <application> element.
     */
    private val manifestIconNames = mutableSetOf<String>()

    // -------------------------------------------------------------------------
    // ResourceXmlDetector overrides
    // -------------------------------------------------------------------------

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.MIPMAP ||
            folderType == ResourceFolderType.DRAWABLE
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            TAG_ADAPTIVE_ICON,
            TAG_APPLICATION
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_ADAPTIVE_ICON -> handleAdaptiveIcon(context, element)
            TAG_APPLICATION -> handleApplicationTag(context, element)
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun handleAdaptiveIcon(context: XmlContext, element: Element) {
        val resourceName = getResourceName(context) ?: return

        val hasMonochrome = element.getElementsByTagName(TAG_MONOCHROME).length > 0

        if (hasMonochrome) {
            iconsWithMonochrome.add(resourceName)
            // Remove from the "without" map in case a lower-API variant was seen first
            iconsWithoutMonochrome.remove(resourceName)
        } else {
            // Only record as missing if we haven't already seen a variant WITH monochrome
            if (resourceName !in iconsWithMonochrome) {
                iconsWithoutMonochrome[resourceName] = Pair(context, element)
            }
        }
    }

    private fun handleApplicationTag(context: XmlContext, element: Element) {
        // Only process the manifest
        if (context.file.name != ANDROID_MANIFEST_XML) return

        fun addIconRef(attrName: String) {
            val value = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android",
                attrName
            ).takeIf { it.isNotEmpty() }
                ?: element.getAttribute(attrName).takeIf { it.isNotEmpty() }
                ?: return
            // value is like @mipmap/ic_launcher or @drawable/ic_launcher
            val name = value.substringAfterLast('/')
            if (name.isNotEmpty()) {
                manifestIconNames.add(name)
            }
        }

        addIconRef(ATTR_ICON)
        addIconRef(ATTR_ROUND_ICON)
    }

    /**
     * Derive the resource name from the file path.
     * e.g. res/mipmap-anydpi-v26/ic_launcher.xml  ->  "ic_launcher"
     */
    private fun getResourceName(context: XmlContext): String? {
        val fileName = context.file.name
        if (!fileName.endsWith(".xml")) return null
        return fileName.removeSuffix(".xml").takeIf { it.isNotEmpty() }
    }

    // -------------------------------------------------------------------------
    // After all files are scanned, report issues
    // -------------------------------------------------------------------------

    override fun afterCheckRootProject(context: Context) {
        for ((resourceName, pair) in iconsWithoutMonochrome) {
            // Only report icons that are actually used as launcher icons in the manifest.
            // If we collected no manifest icons (manifest not in scope), report all adaptive icons.
            if (manifestIconNames.isNotEmpty() && resourceName !in manifestIconNames) continue

            val (xmlContext, element) = pair
            xmlContext.report(
                issue = ISSUE,
                scope = element,
                location = xmlContext.getLocation(element),
                message = "Adaptive icon `$resourceName` does not include a `<monochrome>` " +
                    "layer. Providing a monochrome layer is strongly recommended so the system " +
                    "can properly tint your icon based on the user's wallpaper and theme. " +
                    "Without it, Android 16 QPR 2+ will tint the color icon which may look " +
                    "unusual, and earlier versions will show an untinted icon that looks " +
                    "inconsistent with themed icons."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Companion object
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG_ADAPTIVE_ICON = "adaptive-icon"
        private const val TAG_MONOCHROME = "monochrome"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MonochromeLauncherIcon",
            briefDescription = "Monochrome icon is not defined",
            explanation = """
                The system may use the coloring of the user's chosen wallpaper and theme to \
                tint app icons. Providing a `<monochrome>` layer (which will be used for \
                tinting) for every adaptive icon is strongly recommended, otherwise Android 16 \
                QPR 2 and above will simply tint the color version of the icon, which may look \
                unusual. Devices running earlier Android versions will (with no monochrome layer) \
                show the untinted color icon for your app, which will look inconsistent.
            """,
            category = Category.ICONS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                MonochromeLauncherIconDetector::class.java,
                EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.MANIFEST),
                EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
        )
    }
}