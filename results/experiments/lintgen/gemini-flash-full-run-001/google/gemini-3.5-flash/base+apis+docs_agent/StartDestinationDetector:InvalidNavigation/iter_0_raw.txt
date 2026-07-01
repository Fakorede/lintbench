package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class StartDestinationDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All `<navigation>` elements must have a start destination specified, and it must be a direct child of that `<navigation>`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var startDest = element.getAttributeNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_START_DESTINATION)
        if (startDest.isNullOrEmpty()) {
            startDest = element.getAttribute(SdkConstants.ATTR_START_DESTINATION)
        }

        if (startDest.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestId = getResourceId(startDest)
        if (startDestId == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        var found = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                var childId = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)
                if (childId.isNullOrEmpty()) {
                    childId = childElement.getAttribute(SdkConstants.ATTR_ID)
                }
                if (getResourceId(childId) == startDestId) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            val attributeNode = element.getAttributeNodeNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_START_DESTINATION)
                ?: element.getAttributeNode(SdkConstants.ATTR_START_DESTINATION)
            val location = if (attributeNode != null) context.getLocation(attributeNode) else context.getNameLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "The start destination must be a direct child of the <navigation> element"
            )
        }
    }

    private fun getResourceId(id: String?): String? {
        if (id == null) return null
        val index = id.lastIndexOf('/')
        return if (index != -1) id.substring(index + 1) else id
    }
}