package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            StartDestinationDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All <navigation> elements must have a start destination specified, and it must be a direct child of that <navigation>.",
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.NAVIGATION

    override fun getApplicableElements(): Collection<String>? =
        listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttribute("app:startDestination")
        if (startDestination.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified"
            )
        }
    }
}