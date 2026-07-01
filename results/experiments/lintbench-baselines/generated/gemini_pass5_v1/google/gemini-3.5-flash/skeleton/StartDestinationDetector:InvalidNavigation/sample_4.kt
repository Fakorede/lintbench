package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
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
        var startDest = element.getAttributeNS("http://schemas.android.com/apk/res-auto", "startDestination")
        if (startDest.isNullOrEmpty()) {
            startDest = element.getAttribute("startDestination")
        }

        if (startDest.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val cleanStartDest = stripIdPrefix(startDest)
        var hasMatchingChild = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                var childId = child.getAttributeNS("http://schemas.android.com/apk/res/android", "id")
                if (childId.isNullOrEmpty()) {
                    childId = child.getAttribute("android:id")
                }
                if (stripIdPrefix(childId) == cleanStartDest) {
                    hasMatchingChild = true
                    break
                }
            }
        }

        if (!hasMatchingChild) {
            val attributeNode = element.getAttributeNodeNS("http://schemas.android.com/apk/res-auto", "startDestination")
                ?: element.getAttributeNode("startDestination")
            val location = if (attributeNode != null) {
                context.getValueLocation(attributeNode)
            } else {
                context.getNameLocation(element)
            }
            context.report(
                ISSUE,
                element,
                location,
                "The start destination `$startDest` must be a direct child of the `<navigation>` element"
            )
        }
    }

    private fun stripIdPrefix(id: String): String {
        return when {
            id.startsWith("@+id/") -> id.substring("@+id/".length)
            id.startsWith("@id/") -> id.substring("@id/".length)
            else -> id
        }
    }
}