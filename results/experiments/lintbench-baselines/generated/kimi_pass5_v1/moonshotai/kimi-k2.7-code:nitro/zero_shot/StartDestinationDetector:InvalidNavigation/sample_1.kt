package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class StartDestinationDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttribute("app:startDestination")
        if (startDestination.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified for this navigation graph"
            )
            return
        }

        val targetId = idName(startDestination) ?: run {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Invalid start destination reference"
            )
            return
        }

        val children = element.childNodes
        var found = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Element.ELEMENT_NODE) continue
            val childElement = child as Element
            if (childElement.tagName == "navigation") continue
            val childId = idName(childElement.getAttribute("android:id"))
            if (childId == targetId) {
                found = true
                break
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Start destination `$startDestination` must be a direct child of this `<navigation>` element"
            )
        }
    }

    private fun idName(value: String): String? {
        if (value.isBlank()) return null
        return value.substringAfterLast('/')
            .removePrefix("@+id/")
            .removePrefix("@id/")
            .takeIf { it.isNotBlank() }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                All `<navigation>` elements must have an `app:startDestination` attribute,
                and the destination it references must be a direct child of that
                `<navigation>` element.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}