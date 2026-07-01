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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNS("http://schemas.android.com/apk/res-auto", "startDestination")
            .takeIf { it.isNotEmpty() }
            ?: element.getAttributeNS("http://schemas.android.com/apk/res/android", "startDestination")
            .takeIf { it.isNotEmpty() }

        if (startDestAttr.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestId = startDestAttr.substringAfter('/')

        var found = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childIdAttr = child.getAttributeNS("http://schemas.android.com/apk/res/android", "id")
                if (childIdAttr.isNotEmpty()) {
                    val childId = childIdAttr.substringAfter('/')
                    if (childId == startDestId) {
                        found = true
                        break
                    }
                }
            }
        }

        if (!found) {
            val attributeNode = element.getAttributeNodeNS("http://schemas.android.com/apk/res-auto", "startDestination")
                ?: element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "startDestination")
            val location = if (attributeNode != null) context.getLocation(attributeNode) else context.getNameLocation(element)
            context.report(
                ISSUE,
                element,
                location,
                "The start destination `$startDestAttr` must be a direct child of the `<navigation>` element"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                All `<navigation>` elements must have a start destination specified, \
                and it must be a direct child of that `<navigation>`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}