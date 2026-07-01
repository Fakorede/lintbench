package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class StartDestinationDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val startDestAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res-auto", "startDestination")
        if (startDestAttr == null || startDestAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestId = stripId(startDestAttr.value)
        if (startDestId.isNullOrEmpty()) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getValueLocation(startDestAttr),
                "No start destination specified"
            )
            return
        }

        var found = false
        var childNode = element.firstChild
        while (childNode != null) {
            if (childNode is org.w3c.dom.Element) {
                val childIdAttr = childNode.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "id")
                if (childIdAttr != null) {
                    val childId = stripId(childIdAttr.value)
                    if (childId == startDestId) {
                        found = true
                        break
                    }
                }
            }
            childNode = childNode.nextSibling
        }

        if (!found) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getValueLocation(startDestAttr),
                "The start destination `$startDestId` must be a direct child of the `<navigation>` element"
            )
        }
    }

    private fun stripId(id: String?): String? {
        if (id == null) return null
        val index = id.lastIndexOf('/')
        return if (index != -1) id.substring(index + 1) else id
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