package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import org.w3c.dom.Element

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            StartDestinationDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All `<navigation>` elements must have a start destination specified, and it must be a direct child of that `<navigation>`.",
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        var startDestNode: org.w3c.dom.Node? = null
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.localName == "startDestination" || attr.nodeName == "app:startDestination") {
                startDestNode = attr
                break
            }
        }

        if (startDestNode == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestValue = startDestNode.nodeValue
        if (startDestValue.isNullOrBlank()) {
            context.report(
                ISSUE,
                startDestNode,
                context.getLocation(startDestNode),
                "No start destination specified"
            )
            return
        }

        val startDestId = startDestValue.substringAfter('/')
        if (startDestId.isEmpty()) {
            context.report(
                ISSUE,
                startDestNode,
                context.getLocation(startDestNode),
                "No start destination specified"
            )
            return
        }

        var found = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childId = child.getAttributeNS("http://schemas.android.com/apk/res/android", "id")
                if (childId.isNotEmpty() && childId.substringAfter('/') == startDestId) {
                    found = true
                    break
                }
                val childAttrs = child.attributes
                for (j in 0 until childAttrs.length) {
                    val attr = childAttrs.item(j)
                    if (attr.localName == "id") {
                        if (attr.nodeValue.substringAfter('/') == startDestId) {
                            found = true
                            break
                        }
                    }
                }
                if (found) break
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                startDestNode,
                context.getLocation(startDestNode),
                "The start destination `$startDestValue` must be a direct child of the `<navigation>` element"
            )
        }
    }
}