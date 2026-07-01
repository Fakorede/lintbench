package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
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
        if (element.tagName != "navigation") return

        var startDestAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res-auto", "startDestination")
        if (startDestAttr == null) {
            startDestAttr = element.getAttributeNode("app:startDestination")
        }

        val startDestVal = startDestAttr?.value
        if (startDestVal.isNullOrEmpty()) {
            context.report(
                issue = ISSUE,
                scope = element,
                location = context.getNameLocation(element),
                message = "No start destination specified"
            )
            return
        }

        val startDestId = startDestVal.substringAfter('/')
        if (startDestId.isEmpty()) {
            context.report(
                issue = ISSUE,
                scope = startDestAttr,
                location = context.getValueLocation(startDestAttr),
                message = "Invalid start destination"
            )
            return
        }

        var found = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                var childId = child.getAttributeNS("http://schemas.android.com/apk/res/android", "id")
                if (childId.isNullOrEmpty()) {
                    childId = child.getAttribute("android:id")
                }
                if (!childId.isNullOrEmpty() && childId.substringAfter('/') == startDestId) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            context.report(
                issue = ISSUE,
                scope = startDestAttr,
                location = context.getValueLocation(startDestAttr),
                message = "The start destination `$startDestVal` must be a direct child of the `<navigation>` element"
            )
        }
    }
}