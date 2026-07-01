package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

class InternalInsetResourceDetector : ResourceXmlDetector() {

    companion object {
        private val INTERNAL_INSET_DIMEN_NAMES = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "system_window_inset_top",
            "system_window_inset_bottom",
            "system_window_inset_left",
            "system_window_inset_right"
        )

        private val INTERNAL_INSET_DIMEN_REGEX = Regex(
            "^@(android:)?dimen/(${INTERNAL_INSET_DIMEN_NAMES.joinToString("|")})$"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI. \
                To get the relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value.matches(INTERNAL_INSET_DIMEN_REGEX)) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Using internal inset dimension resource"
            )
        }
    }
}