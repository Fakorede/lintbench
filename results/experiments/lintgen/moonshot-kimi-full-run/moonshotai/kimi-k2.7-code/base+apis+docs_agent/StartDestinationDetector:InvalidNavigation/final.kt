package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class StartDestinationDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.NAVIGATION

    override fun getApplicableElements(): Collection<String> = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination =
            element.getAttributeNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_START_DESTINATION)

        if (startDestination.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified for this navigation graph"
            )
            return
        }

        val destinationId = startDestination.substringAfterLast("/")
        if (destinationId.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(
                    element,
                    null,
                    SdkConstants.AUTO_URI,
                    SdkConstants.ATTR_START_DESTINATION
                ),
                "Invalid start destination reference"
            )
            return
        }

        val hasMatchingChild = (0 until element.childNodes.length).any { index ->
            val child = element.childNodes.item(index)
            child is Element &&
                child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)
                    .substringAfterLast("/") == destinationId
        }

        if (!hasMatchingChild) {
            context.report(
                ISSUE,
                element,
                context.getElementLocation(
                    element,
                    null,
                    SdkConstants.AUTO_URI,
                    SdkConstants.ATTR_START_DESTINATION
                ),
                "The start destination must be a direct child of this navigation graph"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                All <navigation> elements must specify a start destination using
                app:startDestination, and the referenced destination must be a direct child
                of the navigation graph.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}