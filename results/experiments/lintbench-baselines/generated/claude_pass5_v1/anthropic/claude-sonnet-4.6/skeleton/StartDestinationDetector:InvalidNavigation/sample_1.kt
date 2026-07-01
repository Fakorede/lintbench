package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
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
            explanation = """
                All `<navigation>` elements must have a start destination specified, \
                and it must be a direct child of that `<navigation>`.
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val TAG_NAVIGATION = "navigation"
        private const val ATTR_START_DESTINATION = "startDestination"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val APP_NS = "http://schemas.android.com/apk/res-auto"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Get the startDestination attribute (can be in app namespace or no namespace)
        val startDestination = element.getAttributeNS(APP_NS, ATTR_START_DESTINATION)
            .takeIf { it.isNotEmpty() }
            ?: element.getAttributeNS(ANDROID_NS, ATTR_START_DESTINATION)
                .takeIf { it.isNotEmpty() }
            ?: element.getAttribute(ATTR_START_DESTINATION)
                .takeIf { it.isNotEmpty() }

        if (startDestination.isNullOrEmpty()) {
            context.report(
                issue = ISSUE,
                element = element,
                location = context.getNameLocation(element),
                message = "No start destination specified",
            )
            return
        }

        // Extract the id from the reference (e.g. "@+id/foo" or "@id/foo" -> "foo")
        val destinationId = extractId(startDestination) ?: return

        // Check that the referenced destination is a direct child of this navigation element
        val childNodes = element.childNodes
        var foundDirectChild = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childId = getElementId(child)
                if (childId != null && childId == destinationId) {
                    foundDirectChild = true
                    break
                }
            }
        }

        if (!foundDirectChild) {
            val attrNode = element.getAttributeNodeNS(APP_NS, ATTR_START_DESTINATION)
                ?: element.getAttributeNodeNS(ANDROID_NS, ATTR_START_DESTINATION)
                ?: element.getAttributeNode(ATTR_START_DESTINATION)

            val location = if (attrNode != null) {
                context.getValueLocation(attrNode)
            } else {
                context.getNameLocation(element)
            }

            context.report(
                issue = ISSUE,
                element = element,
                location = location,
                message = "Start destination `$startDestination` is not a direct child of this `<navigation>`",
            )
        }
    }

    private fun extractId(reference: String): String? {
        // Reference is of the form "@+id/foo" or "@id/foo"
        val slashIndex = reference.indexOf('/')
        if (slashIndex == -1) return null
        return reference.substring(slashIndex + 1).takeIf { it.isNotEmpty() }
    }

    private fun getElementId(element: Element): String? {
        val id = element.getAttributeNS(ANDROID_NS, "id")
            .takeIf { it.isNotEmpty() }
            ?: element.getAttribute("android:id")
                .takeIf { it.isNotEmpty() }
            ?: element.getAttribute("id")
                .takeIf { it.isNotEmpty() }
            ?: return null
        return extractId(id)
    }
}