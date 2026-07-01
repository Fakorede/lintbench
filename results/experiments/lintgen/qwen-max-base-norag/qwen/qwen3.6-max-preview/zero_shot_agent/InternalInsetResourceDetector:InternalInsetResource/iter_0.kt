package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import java.util.regex.Pattern

class InternalInsetResourceDetector : ResourceXmlDetector() {

    companion object {
        private val INSET_DIMEN_NAMES = listOf(
            "status_bar_height",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "system_bar_height"
        )

        private val PATTERN = Pattern.compile("@\\*?android:dimen/(${INSET_DIMEN_NAMES.joinToString("|")})\\b")

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = "The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(InternalInsetResourceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value.isNotEmpty()) {
            val matcher = PATTERN.matcher(value)
            if (matcher.find()) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Using internal inset dimension resource `${matcher.group(1)}`"
                )
            }
        }
    }
}