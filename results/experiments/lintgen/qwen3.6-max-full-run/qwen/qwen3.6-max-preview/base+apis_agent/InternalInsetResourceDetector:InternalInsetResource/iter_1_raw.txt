package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class InternalInsetResourceDetector : Detector(), XmlScanner {

    companion object {
        private val INSET_DIMENS = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width",
            "status_bar_height_landscape",
            "navigation_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height_portrait"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
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
        if (value.startsWith("@android:dimen/") || value.startsWith("@*android:dimen/")) {
            val name = value.substringAfterLast('/')
            if (name in INSET_DIMENS) {
                context.report(
                    ISSUE,
                    context.getLocation(attribute),
                    "Use `WindowInsetsCompat` instead of internal inset dimension resources"
                )
            }
        }
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val text = element.textContent?.trim() ?: return
        if (text.startsWith("@android:dimen/") || text.startsWith("@*android:dimen/")) {
            val name = text.substringAfterLast('/')
            if (name in INSET_DIMENS) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Use `WindowInsetsCompat` instead of internal inset dimension resources"
                )
            }
        }
    }
}