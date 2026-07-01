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
        private const val ANDROID_APP_NS = "http://schemas.android.com/apk/res-auto"
        private const val ATTR_ID = "id"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Get the startDestination attribute value (from app namespace)
        val startDestination = element.getAttributeNS(ANDROID_APP_NS, ATTR_START_DESTINATION)
            .takeIf { it.isNotEmpty() }
            ?: element.getAttribute(ATTR_START_DESTINATION).takeIf { it.isNotEmpty() }

        if (startDestination.isNullOrEmpty()) {
            context.report(
                issue = ISSUE,
                element = element,
                location = context.getNameLocation(element),
                message = "No start destination specified",
            )
            return
        }

        // Resolve the reference: strip leading @id/, @+id/, etc.
        val referencedId = resolveIdReference(startDestination)
        if (referencedId == null) {
            context.report(
                issue = ISSUE,
                element = element,
                location = context.getNameLocation(element),
                message = "No start destination specified",
            )
            return
        }

        // Check that the referenced id corresponds to a direct child of this navigation element
        val childNodes = element.childNodes
        var foundChild = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childId = child.getAttributeNS(ANDROID_NS, ATTR_ID)
                    .takeIf { it.isNotEmpty() }
                    ?: child.getAttribute("android:id").takeIf { it.isNotEmpty() }

                val resolvedChildId = if (childId != null) resolveIdReference(childId) else null
                if (resolvedChildId != null && resolvedChildId == referencedId) {
                    foundChild = true
                    break
                }
            }
        }

        if (!foundChild) {
            context.report(
                issue = ISSUE,
                element = element,
                location = context.getNameLocation(element),
                message = "Start destination `$startDestination` is not a direct child of this `<navigation>`",
            )
        }
    }

    /**
     * Resolves an Android resource ID reference such as:
     *   @id/foo, @+id/foo, @android:id/foo
     * Returns just the local name (e.g. "foo"), or null if it cannot be parsed.
     */
    private fun resolveIdReference(reference: String): String? {
        val trimmed = reference.trim()
        // Match patterns like @[+][package:]id/name
        val regex = Regex("""^@\+?(?:[^:]+:)?id/(.+)$""")
        val match = regex.find(trimmed) ?: return null
        return match.groupValues[1].takeIf { it.isNotEmpty() }
    }
}