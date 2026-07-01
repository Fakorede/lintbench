package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Collections

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
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

    override fun getApplicableElements(): Collection<String>? {
        return Collections.singletonList("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttributeNS(SdkConstants.AUTO_URI, "startDestination")

        if (startDestination.isNullOrEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val normalizedStartDest = normalizeId(startDestination)
        var found = false

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val childId = childElement.getAttributeNS(SdkConstants.ANDROID_URI, "id")
                if (normalizeId(childId) == normalizedStartDest) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Start destination must be a direct child of this navigation element"
            )
        }
    }

    private fun normalizeId(id: String?): String? {
        if (id.isNullOrEmpty()) return null
        return id.removePrefix("@+id/").removePrefix("@id/")
    }
}