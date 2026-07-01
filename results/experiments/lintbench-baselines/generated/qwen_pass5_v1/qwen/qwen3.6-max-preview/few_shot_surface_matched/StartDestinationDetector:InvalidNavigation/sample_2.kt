package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class StartDestinationDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(context: XmlContext, rootElement: Element): Boolean = true

    override fun getApplicableElements(): Collection<String> = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttributeNS("http://schemas.android.com/apk/res-auto", "startDestination")
        if (startDestination.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All `<navigation>` elements must have a start destination specified, and it must be a direct child of that `<navigation>`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(StartDestinationDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}