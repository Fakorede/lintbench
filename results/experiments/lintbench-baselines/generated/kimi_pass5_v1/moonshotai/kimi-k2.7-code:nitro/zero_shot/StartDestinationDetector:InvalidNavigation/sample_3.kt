package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class StartDestinationDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != "navigation") {
            return
        }

        val startDestinationAttr = findAttribute(element, "startDestination")
        if (startDestinationAttr == null) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "No start destination specified for this <navigation>"
            )
            return
        }

        val startDestination = startDestinationAttr.value
        val startId = parseIdReference(startDestination)
        if (startId == null) {
            context.report(
                ISSUE,
                context.getLocation(startDestinationAttr),
                "The start destination value is not a valid id reference"
            )
            return
        }

        val directChildIds = mutableSetOf<String>()
        for (i in 0 until element.childNodes.length) {
            val child = element.childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val idAttr = findAttribute(childElement, "id")
                val idValue = idAttr?.value
                parseIdReference(idValue)?.let { directChildIds.add(it) }
            }
        }

        if (startId !in directChildIds) {
            context.report(
                ISSUE,
                context.getLocation(startDestinationAttr),
                "The start destination \"$startId\" is not a direct child of this <navigation>"
            )
        }
    }

    private fun findAttribute(element: Element, localName: String): Attr? {
        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? Attr ?: continue
            if (attr.localName == localName) {
                return attr
            }
        }
        return null
    }

    private fun parseIdReference(value: String?): String? {
        return when {
            value == null -> null
            value.startsWith("@+id/") -> value.removePrefix("@+id/")
            value.startsWith("@id/") -> value.removePrefix("@id/")
            value.startsWith("@android:id/") -> value.removePrefix("@android:id/")
            else -> null
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                Every `<navigation>` element must declare a `app:startDestination` attribute.
                The destination it references must be a direct child of the same `<navigation>` element.
            """.trimIndent(),
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