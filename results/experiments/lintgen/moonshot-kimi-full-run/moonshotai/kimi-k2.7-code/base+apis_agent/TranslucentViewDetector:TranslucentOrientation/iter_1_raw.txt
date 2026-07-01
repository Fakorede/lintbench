package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_SCREEN_ORIENTATION
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class TranslucentViewDetector : Detector(), Detector.XmlScanner {

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "TranslucentOrientation",
            briefDescription = "Mixing screenOrientation and translucency",
            explanation = """
                Specifying a fixed screenOrientation with a translucent theme is not supported \
                on apps with targetSdkVersion O or greater, because another activity visible \
                behind this activity may request a conflicting orientation. On Android O and \
                above the app will throw an exception when this is detected.
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

        private const val ANDROID_O = 26
        private const val WINDOW_IS_TRANSLUCENT = "android:windowIsTranslucent"
    }

    private data class StyleInfo(
        val name: String,
        val parent: String?,
        val translucent: Boolean
    )

    private data class ActivityInfo(
        val location: Location,
        val screenOrientation: String,
        val theme: String?
    )

    private val styles = mutableMapOf<String, StyleInfo>()
    private val activities = mutableListOf<ActivityInfo>()
    private var applicationTheme: String? = null

    override fun getApplicableElements(): Collection<String> =
        listOf(TAG_ACTIVITY, TAG_APPLICATION, TAG_STYLE)

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun beforeCheckEachProject(context: Context) {
        styles.clear()
        activities.clear()
        applicationTheme = null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_STYLE -> visitStyle(element)
            TAG_ACTIVITY -> visitActivity(context, element)
            TAG_APPLICATION -> {
                applicationTheme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
                    .takeIf { it.isNotEmpty() }
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.project.targetSdkVersion.featureLevel < ANDROID_O) return

        val appTheme = applicationTheme
        for (activity in activities) {
            val orientation = activity.screenOrientation
            if (orientation == "unspecified" || orientation == "behind") continue

            val theme = activity.theme ?: appTheme ?: continue
            if (isTranslucent(theme, styles)) {
                val message = "Cannot use a fixed screenOrientation ($orientation) with a translucent theme"
                context.report(Incident(ISSUE, activity.location, message))
            }
        }
    }

    private fun visitStyle(element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val explicitParent = element.getAttribute(ATTR_PARENT).takeIf { it.isNotEmpty() }
        val parent = explicitParent ?: deriveParentFromName(name)

        var translucent = false
        var child = element.firstChild
        while (child != null) {
            if (child is Element && child.tagName == SdkConstants.TAG_ITEM) {
                val itemName = child.getAttribute(ATTR_NAME)
                if (itemName == WINDOW_IS_TRANSLUCENT || itemName == "windowIsTranslucent") {
                    if (child.textContent?.trim() == "true") {
                        translucent = true
                        break
                    }
                }
            }
            child = child.nextSibling
        }

        styles[name] = StyleInfo(name, parent, translucent)
    }

    private fun deriveParentFromName(name: String): String? {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else null
    }

    private fun isTranslucent(themeRef: String, styles: Map<String, StyleInfo>): Boolean {
        val normalized = normalizeThemeRef(themeRef)

        if (normalized.startsWith("@android:style/")) {
            val platformName = normalized.substringAfter("@android:style/")
            if (platformName.contains("Translucent", ignoreCase = true)) {
                return true
            }
        }

        val styleName = if (normalized.startsWith("@style/")) {
            normalized.substringAfter("@style/")
        } else {
            normalized
        }

        val style = styles[styleName] ?: return false
        if (style.translucent) return true
        val parent = style.parent ?: return false
        return isTranslucent(parent, styles)
    }

    private fun normalizeThemeRef(ref: String): String {
        return when {
            ref.startsWith("@style/") || ref.startsWith("@android:style/") -> ref
            ref.startsWith("style/") -> "@$ref"
            ref.startsWith("android:style/") -> "@$ref"
            ref.startsWith("@") -> ref
            else -> "@style/$ref"
        }
    }

    private fun visitActivity(context: XmlContext, element: Element) {
        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
        if (orientation.isEmpty()) return

        val theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME)
            .takeIf { it.isNotEmpty() }

        activities.add(
            ActivityInfo(
                location = context.getLocation(element),
                screenOrientation = orientation,
                theme = theme
            )
        )
    }
}