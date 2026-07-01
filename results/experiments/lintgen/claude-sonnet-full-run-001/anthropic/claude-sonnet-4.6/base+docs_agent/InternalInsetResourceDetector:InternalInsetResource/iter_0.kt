package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

/**
 * Detector that flags usage of internal inset dimension resources such as
 * `@android:dimen/status_bar_height`, `@android:dimen/navigation_bar_height`, etc.
 *
 * These are internal platform resources and not a supported API for retrieving insets.
 * Apps should use `androidx.core.view.WindowInsetsCompat` instead.
 */
class InternalInsetResourceDetector : Detector(), XmlScanner {

    companion object {

        /** The set of known internal inset dimension resource names. */
        private val INTERNAL_INSET_RESOURCES = setOf(
            "status_bar_height",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "navigation_bar_frame_height",
            "navigation_bar_interaction_mode",
            "system_bar_height",
            "ime_height",
            "taskbar_frame_height",
            "taskbar_height",
            "gesture_nav_bar_height",
            "caption_bar_height",
            "display_cutout_safe_inset_top",
            "display_cutout_safe_inset_bottom",
            "display_cutout_safe_inset_left",
            "display_cutout_safe_inset_right"
        )

        private const val EXPLANATION =
            "The internal inset dimension resources are not a supported way to retrieve the " +
                "relevant insets for your application. The insets are dynamic values that can " +
                "change while your app is visible, and your app's window may not intersect with " +
                "the system UI.\n" +
                "\n" +
                "To get the relevant value for your app and listen to updates, use " +
                "`androidx.core.view.WindowInsetsCompat` and related APIs."

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        /** Prefix used for android namespace resource references in XML attribute values. */
        private const val ANDROID_DIMEN_PREFIX = "@android:dimen/"

        /** Alternative prefix sometimes seen. */
        private const val ANDROID_DIMEN_PREFIX_ALT = "@*android:dimen/"
    }

    // -------------------------------------------------------------------------
    // XmlScanner implementation
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String>? = XmlScanner.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            checkAttributeValue(context, attr)
        }
    }

    private fun checkAttributeValue(context: XmlContext, attr: Attr) {
        val value = attr.value ?: return
        val resourceName = extractInternalInsetResourceName(value) ?: return
        if (resourceName in INTERNAL_INSET_RESOURCES) {
            reportIssue(context, attr, resourceName)
        }
    }

    /**
     * If [value] references an internal android dimen resource, returns the resource name;
     * otherwise returns null.
     */
    private fun extractInternalInsetResourceName(value: String): String? {
        val prefix = when {
            value.startsWith(ANDROID_DIMEN_PREFIX) -> ANDROID_DIMEN_PREFIX
            value.startsWith(ANDROID_DIMEN_PREFIX_ALT) -> ANDROID_DIMEN_PREFIX_ALT
            else -> return null
        }
        val name = value.removePrefix(prefix).trim()
        return name.ifEmpty { null }
    }

    private fun reportIssue(context: XmlContext, attr: Attr, resourceName: String) {
        val message =
            "Avoid using internal inset resource `@android:dimen/$resourceName`; " +
                "use `androidx.core.view.WindowInsetsCompat` and related APIs instead"
        context.report(
            issue = ISSUE,
            location = context.getValueLocation(attr),
            message = message
        )
    }
}