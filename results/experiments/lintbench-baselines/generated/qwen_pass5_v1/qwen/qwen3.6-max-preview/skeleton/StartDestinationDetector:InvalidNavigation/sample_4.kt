package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
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

    override fun getApplicableElements(): Collection<String>? = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttribute("app:startDestination")
            .takeIf { it.isNotEmpty() }
            ?: element.getAttribute("startDestination")
            .takeIf { it.isNotEmpty() }
            ?: ""

        if (startDestAttr.isEmpty()) {
            context.report(ISSUE, element, context.getLocation(element), "No start destination specified")
            return
        }

        val destId = startDestAttr.substringAfterLast('/')
        var found = false
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val childId = child.getAttribute("android:id")
                if (childId.isNotEmpty() && childId.substringAfterLast('/') == destId) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Start destination is not a direct child of this navigation graph"
            )
        }
    }
}