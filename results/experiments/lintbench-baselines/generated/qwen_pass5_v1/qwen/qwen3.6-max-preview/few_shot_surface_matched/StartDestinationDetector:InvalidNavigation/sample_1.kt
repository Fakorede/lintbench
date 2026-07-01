package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.xml.XmlFile
import org.w3c.dom.Element
import org.w3c.dom.Node

class StartDestinationDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(context: XmlContext, file: XmlFile): Boolean {
        return file.rootTag?.name == "navigation"
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val autoNs = "http://schemas.android.com/apk/res-auto"
        val startDestAttr = element.getAttributeNodeNS(autoNs, "startDestination")

        if (startDestAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val targetId = stripIdPrefix(startDestAttr.value)
        var found = false
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childEl = child as Element
                val childId = childEl.getAttributeNS("http://schemas.android.com/apk/res/android", "id")
                if (childId.isNotEmpty() && stripIdPrefix(childId) == targetId) {
                    found = true
                    break
                }
            }
            child = child.nextSibling
        }

        if (!found) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getValueLocation(startDestAttr),
                "Start destination must be a direct child of this <navigation> element"
            )
        }
    }

    private fun stripIdPrefix(id: String): String {
        return id.removePrefix("@+id/").removePrefix("@id/")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All <navigation> elements must have a start destination specified, and it must be a direct child of that <navigation>.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(StartDestinationDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}