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

class StartDestinationDetector : ResourceXmlDetector() {

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

    override fun getApplicableElements(): Collection<String> {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "navigation") return

        val startDestAttr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "startDestination")
        if (startDestAttr == null || startDestAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestValue = startDestAttr.value
        val startDestId = getResourceId(startDestValue)

        var found = false
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val childIdAttr = child.getAttributeNS(SdkConstants.ANDROID_URI, "id")
                if (childIdAttr.isNotEmpty() && getResourceId(childIdAttr) == startDestId) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getLocation(startDestAttr),
                "Start destination must be a direct child of the navigation graph"
            )
        }
    }

    private fun getResourceId(value: String): String {
        return value.substringAfter('/')
    }
}