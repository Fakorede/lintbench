package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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

        private const val TAG_NAVIGATION = "navigation"
        private const val ATTR_START_DESTINATION = "startDestination"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val APP_NS = "http://schemas.android.com/apk/res-auto"

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
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Get the startDestination attribute value (check both app: and android: namespaces)
        var startDestination = element.getAttributeNS(APP_NS, ATTR_START_DESTINATION)
        if (startDestination.isNullOrEmpty()) {
            startDestination = element.getAttributeNS(ANDROID_NS, ATTR_START_DESTINATION)
        }
        if (startDestination.isNullOrEmpty()) {
            startDestination = element.getAttribute(ATTR_START_DESTINATION)
        }

        if (startDestination.isNullOrEmpty()) {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(element),
                message = "No start destination specified",
            )
            return
        }

        // Extract the ID from the reference (e.g., "@+id/foo" or "@id/foo" -> "foo")
        val destinationId = extractId(startDestination) ?: run {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(element),
                message = "No start destination specified",
            )
            return
        }

        // Check that the referenced destination is a direct child of this navigation element
        val childElements = getDirectChildElements(element)
        val hasMatchingChild = childElements.any { child ->
            val childId = getElementId(child)
            childId != null && childId == destinationId
        }

        if (!hasMatchingChild) {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(element),
                message = "Start destination `$startDestination` is not a direct child of this `<navigation>`",
            )
        }
    }

    /**
     * Extracts the ID name from a resource reference like "@+id/foo" or "@id/foo".
     */
    private fun extractId(reference: String): String? {
        val idPrefix1 = "@+id/"
        val idPrefix2 = "@id/"
        return when {
            reference.startsWith(idPrefix1) -> reference.substring(idPrefix1.length)
            reference.startsWith(idPrefix2) -> reference.substring(idPrefix2.length)
            else -> null
        }
    }

    /**
     * Gets the android:id or just id attribute of an element, returning the extracted name.
     */
    private fun getElementId(element: Element): String? {
        var id = element.getAttributeNS(ANDROID_NS, "id")
        if (id.isNullOrEmpty()) {
            id = element.getAttribute("android:id")
        }
        if (id.isNullOrEmpty()) {
            return null
        }
        return extractId(id)
    }

    /**
     * Returns all direct child Elements of the given element.
     */
    private fun getDirectChildElements(element: Element): List<Element> {
        val children = mutableListOf<Element>()
        val nodeList = element.childNodes
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node is Element) {
                children.add(node)
            }
        }
        return children
    }
}