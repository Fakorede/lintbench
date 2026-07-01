package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class StartDestinationDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_NAVIGATION)

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttributeNS(
            SdkConstants.AUTO_URI,
            SdkConstants.ATTR_START_DESTINATION
        )

        if (startDestination.isBlank()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val targetName = stripResourceReference(startDestination)
        if (targetName.isEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        var found = false
        var child: Node? = element.firstChild
        while (child != null) {
            if (child is Element) {
                val childId = child.getAttributeNS(
                    SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_ID
                )
                if (stripResourceReference(childId) == targetName) {
                    found = true
                    break
                }
            }
            child = child.nextSibling
        }

        if (!found) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "The start destination must be a direct child of this <navigation> element"
            )
        }
    }

    private fun stripResourceReference(value: String): String {
        if (value.isBlank()) return ""
        var name = value
        val slash = name.lastIndexOf('/')
        if (slash != -1) {
            name = name.substring(slash + 1)
        }
        name = when {
            name.startsWith("@+id:") -> name.substring(6)
            name.startsWith("@id:") -> name.substring(5)
            name.startsWith("@+id/") -> name.substring(5)
            name.startsWith("@id/") -> name.substring(4)
            name.startsWith("@+") -> name.substring(2)
            name.startsWith("@") -> name.substring(1)
            else -> name
        }
        return name
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                Every <navigation> element must declare a start destination using \
                app:startDestination, and the referenced destination must be a direct child of \
                that <navigation> element.
            """,
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