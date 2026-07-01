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

    override fun getApplicableElements(): Collection<String>? = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDest = element.getAttributeNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_START_DESTINATION)
            .takeIf { it.isNotEmpty() }
            ?: element.getAttribute(SdkConstants.ATTR_START_DESTINATION)

        if (startDest.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val destId = startDest.removePrefix("@+id/").removePrefix("@id/")
        val children = element.childNodes
        val found = (0 until children.length).any { i ->
            val child = children.item(i)
            child.nodeType == Node.ELEMENT_NODE &&
                (child as Element).getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)
                    .removePrefix("@+id/").removePrefix("@id/") == destId
        }

        if (!found) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Start destination is not a direct child of this navigation element"
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
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}