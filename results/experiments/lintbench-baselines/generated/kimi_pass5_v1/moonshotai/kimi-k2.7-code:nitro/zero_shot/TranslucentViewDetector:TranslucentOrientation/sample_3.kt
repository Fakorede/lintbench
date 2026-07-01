package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), Detector.XmlScanner {

    private data class StyleInfo(
        val name: String,
        val parent: String?,
        val parentIsFramework: Boolean,
        val items: Map<String, String>
    )

    private data class PendingActivity(
        val context: XmlContext,
        val element: Element,
        val location: Location,
        val orientation: String,
        val themeRef: String
    )

    private val styles = mutableMapOf<String, StyleInfo>()
    private val pendingActivities = mutableListOf<PendingActivity>()

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_ACTIVITY, SdkConstants.TAG_STYLE)

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            SdkConstants.TAG_STYLE -> recordStyle(element)
            SdkConstants.TAG_ACTIVITY -> {
                if (context.file.name == SdkConstants.ANDROID_MANIFEST_XML) {
                    recordActivity(context, element)
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {}

    override fun beforeCheckRootProject(context: Context) {
        styles.clear()
        pendingActivities.clear()
    }

    override fun afterCheckRootProject(context: Context) {
        val translucent = computeTranslucentStyles()
        for (pending in pendingActivities) {
            val (isFramework, styleName) = parseStyleReference(pending.themeRef) ?: continue
            val isTranslucent = if (isFramework) {
                styleName.contains(TRANSLUCENT)
            } else {
                styleName in translucent
            }
            if (isTranslucent) {
                pending.context.report(
                    ISSUE,
                    pending.element,
                    pending.location,
                    "This activity declares a fixed screenOrientation (\"${pending.orientation}\") with a translucent theme. " +
                        "This combination is not supported on apps with targetSdkVersion O or higher and will throw an exception on Android O+ devices. " +
                        "Either remove android:screenOrientation or remove android:windowIsTranslucent from the theme."
                )
            }
        }
    }

    private fun recordStyle(element: Element) {
        val name = getAttributeValue(element, SdkConstants.ATTR_NAME)
        if (name.isEmpty()) return

        val parentAttr = getAttributeValue(element, SdkConstants.ATTR_PARENT)
        val parsedParent = parseStyleReference(parentAttr)

        val parent: String?
        val parentIsFramework: Boolean
        when {
            parentAttr.isEmpty() -> {
                val lastDot = name.lastIndexOf('.')
                parent = if (lastDot > 0) name.substring(0, lastDot) else null
                parentIsFramework = false
            }
            parsedParent != null -> {
                parent = parsedParent.second
                parentIsFramework = parsedParent.first
            }
            else -> {
                parent = parentAttr
                parentIsFramework = false
            }
        }

        val items = mutableMapOf<String, String>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName != SdkConstants.TAG_ITEM) continue

            val itemName = getAttributeValue(child, SdkConstants.ATTR_NAME)
            if (itemName.isEmpty()) continue

            val normalizedName = if (itemName.startsWith(ANDROID_PREFIX)) {
                itemName.substring(ANDROID_PREFIX.length)
            } else {
                itemName
            }
            items[normalizedName] = child.textContent?.trim().orEmpty()
        }

        styles[name] = StyleInfo(name, parent, parentIsFramework, items)
    }

    private fun recordActivity(context: XmlContext, element: Element) {
        val orientation = getAttributeValue(element, SdkConstants.ATTR_SCREEN_ORIENTATION)
        if (orientation.isEmpty() ||
            orientation == UNSPECIFIED ||
            orientation == BEHIND ||
            orientation !in FIXED_ORIENTATIONS
        ) {
            return
        }

        val themeRef = getAttributeValue(element, SdkConstants.ATTR_THEME)
        if (themeRef.isEmpty()) return

        pendingActivities.add(
            PendingActivity(
                context,
                element,
                context.getLocation(element),
                orientation,
                themeRef
            )
        )
    }

    private fun computeTranslucentStyles(): Set<String> {
        val translucent = mutableSetOf<String>()

        for (style in styles.values) {
            if (style.items[WINDOW_IS_TRANSLUCENT] == SdkConstants.VALUE_TRUE) {
                translucent.add(style.name)
            }
        }

        for (style in styles.values) {
            val parent = style.parent
            if (parent != null &&
                style.parentIsFramework &&
                parent.contains(TRANSLUCENT) &&
                style.items[WINDOW_IS_TRANSLUCENT] != SdkConstants.VALUE_FALSE
            ) {
                translucent.add(style.name)
            }
        }

        var changed = true
        while (changed) {
            changed = false
            for (style in styles.values) {
                if (style.name in translucent) continue
                if (style.items[WINDOW_IS_TRANSLUCENT] == SdkConstants.VALUE_FALSE) continue

                val parent = style.parent ?: continue
                if (parent in translucent) {
                    translucent.add(style.name)
                    changed = true
                }
            }
        }

        return translucent
    }

    private fun getAttributeValue(element: Element, localName: String): String {
        var value = element.getAttribute(localName)
        if (value.isEmpty()) {
            value = element.getAttribute("android:$localName")
        }
        return value
    }

    private fun parseStyleReference(ref: String): Pair<Boolean, String>? {
        return when {
            ref.startsWith(STYLE_PREFIX) -> false to ref.substring(STYLE_PREFIX.length)
            ref.startsWith(ANDROID_STYLE_PREFIX) -> true to ref.substring(ANDROID_STYLE_PREFIX.length)
            ref.startsWith(ANDROID_STAR_STYLE_PREFIX) -> true to ref.substring(ANDROID_STAR_STYLE_PREFIX.length)
            ref.startsWith(STAR_STYLE_PREFIX) -> false to ref.substring(STAR_STYLE_PREFIX.length)
            else -> null
        }
    }

    companion object {
        private const val WINDOW_IS_TRANSLUCENT = "windowIsTranslucent"
        private const val TRANSLUCENT = "Translucent"
        private const val ANDROID_PREFIX = "android:"
        private const val UNSPECIFIED = "unspecified"
        private const val BEHIND = "behind"
        private const val STYLE_PREFIX = "@style/"
        private const val ANDROID_STYLE_PREFIX = "@android:style/"
        private const val ANDROID_STAR_STYLE_PREFIX = "@*android:style/"
        private const val STAR_STYLE_PREFIX = "@*style/"

        private val FIXED_ORIENTATIONS = setOf(
            "landscape",
            "portrait",
            "reverseLandscape",
            "reversePortrait",
            "sensorLandscape",
            "sensorPortrait",
            "userLandscape",
            "userPortrait",
            "locked",
            "nosensor"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation with a translucent theme",
            explanation = """
                Specifying a fixed screenOrientation with a translucent theme is not supported on apps with targetSdkVersion O or greater. If another activity visible behind the translucent activity requests a conflicting orientation, devices running Android O or higher throw an IllegalArgumentException.

                To fix this, either remove the fixed android:screenOrientation or remove android:windowIsTranslucent from the activity's theme.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                TranslucentViewDetector::class.java,
                Scope.MANIFEST_SCOPE,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}