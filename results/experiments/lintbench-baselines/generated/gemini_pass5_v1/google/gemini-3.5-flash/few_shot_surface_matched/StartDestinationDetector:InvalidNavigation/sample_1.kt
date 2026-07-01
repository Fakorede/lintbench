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

        val startDest = extractId(startDestAttr.value)
        if (startDest.isEmpty()) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getValueLocation(startDestAttr),
                "No start destination specified"
            )
            return
        }

        var found = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val childNode = childNodes.item(i)
            if (childNode is org.w3c.dom.Element) {
                val childIdAttr = childNode.getAttributeNS("http://schemas.android.com/apk/res/android", "id")
                if (childIdAttr != null && childIdAttr.isNotEmpty()) {
                    val childId = extractId(childIdAttr)
                    if (childId == startDest) {
                        found = true
                        break
                    }
                }
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getValueLocation(startDestAttr),
                "Start destination `$startDest` is not a direct child of this navigation graph"
            )
        }
    }

    private fun extractId(value: String): String {
        return when {
            value.startsWith("@id/") -> value.substring(4)
            value.startsWith("@+id/") -> value.substring(5)
            else -> value
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
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}