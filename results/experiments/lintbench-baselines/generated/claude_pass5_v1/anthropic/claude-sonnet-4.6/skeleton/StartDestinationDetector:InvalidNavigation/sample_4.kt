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
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val APP_NS = "http://schemas.android.com/apk/res-auto"
        private const val ATTR_START_DESTINATION = "startDestination"
        private const val ATTR_ID = "id"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Get the startDestination attribute (in app namespace)
        val startDestination = element.getAttributeNS(APP_NS, ATTR_START_DESTINATION)
            .takeIf { it.isNotEmpty() }
            ?: element.getAttribute("app:$ATTR_START_DESTINATION")
                .takeIf { it.isNotEmpty() }

        if (startDestination.isNullOrEmpty()) {
            context.report(
                issue = ISSUE,
                location = context.getElementLocation(element),
                message = "No start destination specified",
            )
            return
        }

        // Extract the ID value from the reference (e.g. "@id/foo" or "@+id/foo" -> "foo")
        val startDestId = extractId(startDestination) ?: run {
            context.report(
                issue = ISSUE,
                location = context.getElementLocation(element),
                message = "No start destination specified",
            )
            return
        }

        // Check that the start destination refers to a direct child of this navigation element
        val childNodes = element.childNodes
        var found = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childId = child.getAttributeNS(ANDROID_NS, ATTR_ID)
                    .takeIf { it.isNotEmpty() }
                    ?: child.getAttribute("android:$ATTR_ID")
                        .takeIf { it.isNotEmpty() }

                if (childId != null) {
                    val childIdValue = extractId(childId)
                    if (childIdValue == startDestId) {
                        found = true
                        break
                    }
                }
            }
        }

        if (!found) {
            context.report(
                issue = ISSUE,
                location = context.getElementLocation(element),
                message = "Start destination `$startDestination` is not a direct child of this `<navigation>`",
            )
        }
    }

    /**
     * Extracts the ID name from a resource reference such as "@id/foo" or "@+id/foo".
     * Returns null if the string doesn't match the expected format.
     */
    private fun extractId(reference: String): String? {
        val trimmed = reference.trim()
        val idPrefix = when {
            trimmed.startsWith("@+id/") -> "@+id/"
            trimmed.startsWith("@id/") -> "@id/"
            trimmed.startsWith("@android:id/") -> "@android:id/"
            else -> return null
        }
        val name = trimmed.removePrefix(idPrefix)
        return name.takeIf { it.isNotEmpty() }
    }
}