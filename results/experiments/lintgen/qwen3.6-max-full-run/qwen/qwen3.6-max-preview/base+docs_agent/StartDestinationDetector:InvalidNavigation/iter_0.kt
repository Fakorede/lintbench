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

class StartDestinationDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All `<navigation>` elements must have a start destination specified, and it must be a direct child of that `<navigation>`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(StartDestinationDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNS(SdkConstants.AUTO_URI, "startDestination")
        if (startDestAttr.isNullOrEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestId = extractIdName(startDestAttr)
        val children = element.childNodes
        var isDirectChild = false

        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                val childId = node.getAttributeNS(SdkConstants.ANDROID_URI, "id")
                if (extractIdName(childId) == startDestId) {
                    isDirectChild = true
                    break
                }
            }
        }

        if (!isDirectChild) {
            val attrNode = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "startDestination")
            val location = if (attrNode != null) context.getLocation(attrNode) else context.getLocation(element)
            context.report(
                ISSUE,
                location,
                "Start destination is not a direct child of this <navigation>"
            )
        }
    }

    private fun extractIdName(idAttr: String?): String? {
        if (idAttr.isNullOrEmpty()) return null
        return idAttr.substringAfterLast('/')
    }
}