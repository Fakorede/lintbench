package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class InternalInsetResourceDetector : ResourceXmlDetector() {

    override fun getApplicableAttributes(): Collection<String>? = Detector.ALL_ATTRIBUTES

    override fun visitAttribute(context: XmlContext, attribute: Node) {
        checkValue(context, attribute, attribute.nodeValue)
    }

    override fun getApplicableElements(): Collection<String>? = Detector.ALL_ELEMENTS

    override fun visitElement(context: XmlContext, element: Element) {
        val text = element.textContent?.trim()
        if (!text.isNullOrEmpty()) {
            checkValue(context, element, text)
        }
    }

    private fun checkValue(context: XmlContext, node: Node, value: String) {
        val prefix = when {
            value.startsWith("@android:dimen/") -> "@android:dimen/"
            value.startsWith("@*android:dimen/") -> "@*android:dimen/"
            else -> null
        }
        if (prefix != null) {
            val resourceName = value.substring(prefix.length)
            if (INTERNAL_INSET_DIMENS.contains(resourceName)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using internal inset dimension resource `$resourceName`"
                )
            }
        }
    }

    companion object {
        private val INTERNAL_INSET_DIMENS = setOf(
            "status_bar_height",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "system_bar_height"
        )

        val ISSUE = Issue.create(
            "InternalInsetResource",
            "Using internal inset dimension resource",
            "The internal inset dimension resources are not a supported way to  " +
            "retrieve the relevant insets for your application. The insets are  " +
            "dynamic values that can change while your app is visible, and your  " +
            "app's window may not intersect with the system UI.  " +
            "To get the relevant value for your app and listen to updates, use  " +
            "`androidx.core.view.WindowInsetsCompat` and related APIs.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(InternalInsetResourceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}