package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttributeNS("http://schemas.android.com/apk/res-auto", "startDestination")
        if (startDestination.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val destId = startDestination.removePrefix("@+id/").removePrefix("@id/")
        var found = false
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val childId = child.getAttributeNS("http://schemas.android.com/apk/res/android", "id")
                    .removePrefix("@+id/").removePrefix("@id/")
                if (childId == destId) {
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
                "Start destination must be a direct child of this <navigation> element"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All <navigation> elements must have a start destination specified, and it must be a direct child of that <navigation>.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(StartDestinationDetector::class.java, Scope.ALL_RESOURCE_FILES)
        )
    }
}