package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class StartDestinationDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "InvalidNavigation",
            "No start destination specified",
            "All `<navigation>` elements must have a start destination specified, and it must be a direct child of that `<navigation>`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(StartDestinationDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("navigation")

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.NAVIGATION

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = findAttribute(element, SdkConstants.ATTR_START_DESTINATION)
        val startDestValue = startDestAttr?.value

        if (startDestValue.isNullOrEmpty()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val destId = extractId(startDestValue) ?: return

        val children = element.childNodes
        var found = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val childIdAttr = findAttribute(childElement, SdkConstants.ATTR_ID)
                if (destId == extractId(childIdAttr?.value)) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                context.getLocation(startDestAttr ?: element),
                "Start destination must be a direct child of this <navigation> element"
            )
        }
    }

    private fun findAttribute(element: Element, name: String): Attr? {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as Attr
            if (attr.localName == name || attr.name == name) {
                return attr
            }
        }
        return null
    }

    private fun extractId(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        return if ('/' in value) value.substringAfterLast('/') else value
    }
}