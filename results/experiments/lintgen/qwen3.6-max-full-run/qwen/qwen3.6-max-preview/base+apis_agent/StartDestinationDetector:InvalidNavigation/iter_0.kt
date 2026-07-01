package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class StartDestinationDetector : ResourceXmlDetector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            "InvalidNavigation",
            "No start destination specified",
            "All `<navigation>` elements must have a start destination specified, and it must be a direct child of that `<navigation>`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(StartDestinationDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestValue = element.getAttributeValue(SdkConstants.AUTO_URI, SdkConstants.ATTR_START_DESTINATION)
            ?: element.getAttributeValue(SdkConstants.ANDROID_URI, SdkConstants.ATTR_START_DESTINATION)
            ?: element.getAttributeValue(null, SdkConstants.ATTR_START_DESTINATION)

        if (startDestValue.isNullOrEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val destId = extractId(startDestValue) ?: return

        val children = element.childNodes
        var found = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val childIdValue = childElement.getAttributeValue(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)
                    ?: childElement.getAttributeValue(SdkConstants.AUTO_URI, SdkConstants.ATTR_ID)
                    ?: childElement.getAttributeValue(null, SdkConstants.ATTR_ID)
                if (destId == extractId(childIdValue)) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            val attrNode = element.getAttributeNode(SdkConstants.AUTO_URI, SdkConstants.ATTR_START_DESTINATION)
                ?: element.getAttributeNode(SdkConstants.ANDROID_URI, SdkConstants.ATTR_START_DESTINATION)
                ?: element.getAttributeNode(null, SdkConstants.ATTR_START_DESTINATION)
                ?: element

            context.report(
                ISSUE,
                context.getLocation(attrNode),
                "Start destination must be a direct child of this <navigation> element"
            )
        }
    }

    private fun extractId(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        return when {
            value.startsWith("@+id/") -> value.substring(5)
            value.startsWith("@id/") -> value.substring(4)
            value.startsWith("@*id/") -> value.substring(5)
            else -> null
        }
    }

    override fun getIssues(): List<Issue> = listOf(ISSUE)
}