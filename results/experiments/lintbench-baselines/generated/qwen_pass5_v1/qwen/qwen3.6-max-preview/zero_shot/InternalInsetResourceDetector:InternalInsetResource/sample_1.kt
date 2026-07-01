package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class InternalInsetResourceDetector : ResourceXmlDetector() {

    companion object {
        private val INTERNAL_INSET_DIMENS = setOf(
            "status_bar_height",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "system_bar_height"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = "The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(InternalInsetResourceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Node ?: continue
            val value = attr.nodeValue?.trim() ?: continue
            if (value.startsWith("@android:dimen/") || value.startsWith("@*android:dimen/")) {
                val resourceName = value.substringAfterLast('/')
                if (resourceName in INTERNAL_INSET_DIMENS) {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Using internal inset dimension resource `$resourceName`; use `WindowInsetsCompat` instead"
                    )
                }
            }
        }
    }
}