package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.ATTR_WINDOW_IS_TRANSLUCENT
import com.android.SdkConstants.STYLE_RESOURCE_PREFIX
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.SdkConstants.VALUE_TRUE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class TranslucentViewDetector : ResourceXmlDetector() {

    private data class StyleDef(
        val parent: String?,
        val explicit: Boolean,
        var translucent: Boolean = explicit
    )

    private val styleDefinitions = mutableMapOf<String, StyleDef>()
    private val pendingActivities = mutableListOf<Pair<XmlContext, Element>>()

    override fun getApplicableElements(): Collection<String>? =
        listOf(TAG_STYLE, TAG_ACTIVITY)

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun beforeCheckRootProject(context: Context) {
        styleDefinitions.clear()
        pendingActivities.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.nodeName) {
            TAG_STYLE -> {
                if (context.resourceFolderType == ResourceFolderType.VALUES) {
                    parseStyle(element)
                }
            }
            TAG_ACTIVITY -> {
                if (context.file.name == ANDROID_MANIFEST_XML) {
                    val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
                    if (orientation in FIXED_ORIENTATIONS) {
                        val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                        if (theme.isNotBlank()) {
                            pendingActivities.add(context to element)
                        }
                    }
                }
            }
        }
    }

    private fun parseStyle(element: Element) {
        val name = element.getAttribute(ATTR_NAME)
        if (name.isBlank()) return

        val parent = element.getAttribute(ATTR_PARENT).takeIf { it.isNotBlank() }

        val explicit = (0 until element.childNodes.length).any { i ->
            val child = element.childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == TAG_ITEM) {
                val item = child as Element
                val itemName = item.getAttribute(ATTR_NAME)
                (itemName == ATTR_WINDOW_IS_TRANSLUCENT ||
                    itemName == "android:$ATTR_WINDOW_IS_TRANSLUCENT") &&
                    item.textContent == VALUE_TRUE
            } else {
                false
            }
        }

        styleDefinitions[name] = StyleDef(parent, explicit)
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.mainProject.targetSdkVersion < 26) {
            return
        }

        var changed = true
        while (changed) {
            changed = false
            for ((_, def) in styleDefinitions) {
                if (def.translucent) continue
                val parent = def.parent ?: continue

                val parentName = when {
                    parent.startsWith(STYLE_RESOURCE_PREFIX) ->
                        parent.substring(STYLE_RESOURCE_PREFIX.length)
                    parent.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) ->
                        parent.substring(ANDROID_STYLE_RESOURCE_PREFIX.length)
                    else -> parent
                }

                val parentIsTranslucent = if (parent.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
                    parentName in FRAMEWORK_TRANSLUCENT_THEMES
                } else {
                    styleDefinitions[parentName]?.translucent == true ||
                        parent in FRAMEWORK_TRANSLUCENT_THEMES
                }

                if (parentIsTranslucent) {
                    def.translucent = true
                    changed = true
                }
            }
        }

        for ((actContext, element) in pendingActivities) {
            val themeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_THEME)
            val themeValue = themeAttr?.value ?: continue

            val isTranslucent = when {
                themeValue.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) -> {
                    val themeName = themeValue.substring(ANDROID_STYLE_RESOURCE_PREFIX.length)
                    themeName in FRAMEWORK_TRANSLUCENT_THEMES
                }
                themeValue.startsWith(STYLE_RESOURCE_PREFIX) -> {
                    val styleName = themeValue.substring(STYLE_RESOURCE_PREFIX.length)
                    styleDefinitions[styleName]?.translucent == true
                }
                else -> false
            }

            if (isTranslucent) {
                val location = actContext.getLocation(themeAttr ?: element)
                actContext.report(
                    ISSUE,
                    location,
                    "Using a translucent theme while a fixed screenOrientation is requested is not supported on apps targeting API 26+."
                )
            }
        }
    }

    companion object {
        private val FRAMEWORK_TRANSLUCENT_THEMES = setOf(
            "Theme.Translucent",
            "Theme.Translucent.NoTitleBar",
            "Theme.Translucent.NoTitleBar.Fullscreen",
            "Theme.Holo.Translucent",
            "Theme.Holo.NoActionBar.Translucent",
            "Theme.Material.Translucent",
            "Theme.Material.Light.Translucent"
        )

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
            "TranslucentOrientation",
            "Mixing screen orientation and translucency",
            """
                Specifying a fixed screen orientation with a translucent theme is not supported
                on apps with a targetSdkVersion of O or greater. If another activity is visible
                behind this activity with a conflicting orientation request, the device can only
                honor one of the requests and will throw an exception on Android O (API 26) and
                above.
            """,
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(
                TranslucentViewDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )
    }
}