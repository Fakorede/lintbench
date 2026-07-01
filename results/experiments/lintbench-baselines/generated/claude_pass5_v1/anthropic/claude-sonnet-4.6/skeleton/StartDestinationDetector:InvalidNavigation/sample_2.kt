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
        // Get the startDestination attribute (can be in app: or android: namespace)
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

        // Verify that the referenced destination is a direct child of this navigation element
        val childElements = getDirectChildElements(element)
        val hasMatchingChild = childElements.any { child ->
            val childId = child.getAttributeNS(ANDROID_NS, "id")
                .takeIf { it.isNotEmpty() }
                ?: child.getAttribute("android:id")
                    .takeIf { it.isNotEmpty() }
                ?: return@any false
            val childIdName = extractId(childId)
            childIdName == destinationId
        }

        if (!hasMatchingChild) {
            context.report(
                issue = ISSUE,
                element = element,
                location = context.getNameLocation(element),
                message = "Start destination `$startDestination` is not a direct child of this `<navigation>`",
            )
        }
    }

    private fun extractId(reference: String): String? {
        // Handles "@+id/foo", "@id/foo", "foo"
        val slashIndex = reference.lastIndexOf('/')
        return if (slashIndex >= 0) {
            reference.substring(slashIndex + 1).takeIf { it.isNotEmpty() }
        } else {
            reference.takeIf { it.isNotEmpty() }
        }
    }

    private fun getDirectChildElements(element: Element): List<Element> {
        val children = mutableListOf<Element>()
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                children.add(child)
            }
        }
        return children
    }
}