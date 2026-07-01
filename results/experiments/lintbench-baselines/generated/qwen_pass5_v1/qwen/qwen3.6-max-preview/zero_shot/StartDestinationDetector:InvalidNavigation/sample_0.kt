package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.APP_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

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

    override fun getApplicableElements(): Collection<String>? = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNode("startDestination")
            ?: element.getAttributeNodeNS(APP_URI, "startDestination")

        if (startDestAttr == null) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestValue = startDestAttr.value
        val startDestIdName = extractIdName(startDestValue) ?: return

        val children = element.childNodes
        var found = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val childId = child.getAttributeNS(ANDROID_URI, "id")
                if (childId.isNotEmpty()) {
                    val childIdName = extractIdName(childId)
                    if (childIdName == startDestIdName) {
                        found = true
                        break
                    }
                }
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                context.getLocation(startDestAttr),
                "Start destination is not a direct child of this <navigation> element"
            )
        }
    }

    private fun extractIdName(value: String): String? {
        if (value.startsWith("@id/") || value.startsWith("@+id/")) {
            return value.substringAfterLast('/')
        }
        return null
    }
}