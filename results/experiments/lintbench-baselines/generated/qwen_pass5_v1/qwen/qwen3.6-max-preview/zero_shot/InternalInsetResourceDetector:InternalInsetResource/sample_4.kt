package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import java.util.EnumSet

class InternalInsetResourceDetector : Detector(), Detector.XmlScanner {
    companion object {
        private val INSET_DIMENS = setOf(
            "status_bar_height",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "system_bar_height"
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
                EnumSet.of(Scope.RESOURCE_FILE_SCOPE)
            )
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value.isEmpty()) return

        val dimenName = extractDimenName(value) ?: return
        if (dimenName in INSET_DIMENS) {
            context.report(
                ISSUE,
                context.getValueLocation(attribute),
                "Use `WindowInsetsCompat` instead of internal inset dimension resources"
            )
        }
    }

    private fun extractDimenName(value: String): String? {
        return when {
            value.startsWith("@android:dimen/") -> value.substringAfterLast('/')
            value.startsWith("@*android:dimen/") -> value.substringAfterLast('/')
            else -> null
        }
    }
}