package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

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

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName != "navigation") return

        var startDestAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res-auto", "startDestination")
        if (startDestAttr == null) {
            val attributes = element.attributes
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                if (attr.localName == "startDestination") {
                    startDestAttr = attr as? org.w3c.dom.Attr
                    break
                }
            }
        }

        if (startDestAttr == null || startDestAttr.value.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestValue = startDestAttr.value
        val startDestId = if (startDestValue.contains('/')) {
            startDestValue.substringAfter('/')
        } else {
            startDestValue
        }

        var found = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val childNode = childNodes.item(i)
            if (childNode is org.w3c.dom.Element) {
                var childIdAttr = childNode.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "id")
                if (childIdAttr == null) {
                    val attrs = childNode.attributes
                    for (j in 0 until attrs.length) {
                        val attr = attrs.item(j)
                        if (attr.localName == "id") {
                            childIdAttr = attr as? org.w3c.dom.Attr
                            break
                        }
                    }
                }
                if (childIdAttr != null) {
                    val childId = childIdAttr.value.substringAfter('/')
                    if (childId == startDestId) {
                        found = true
                        break
                    }
                }
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getLocation(startDestAttr),
                "The start destination `$startDestId` must be a direct child of the `<navigation>` element"
            )
        }
    }
}