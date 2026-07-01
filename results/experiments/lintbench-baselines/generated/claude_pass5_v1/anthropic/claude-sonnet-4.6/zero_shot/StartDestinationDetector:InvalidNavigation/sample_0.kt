package com.android.tools.lint.checks

import com.android.SdkConstants
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
        private const val TAG_NAVIGATION = "navigation"
        private const val ATTR_START_DESTINATION = "startDestination"
        private const val ATTR_ID = "id"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                All `<navigation>` elements must have a start destination specified, \
                and it must be a direct child of that `<navigation>`.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Get the startDestination attribute value
        val startDestination = element.getAttributeNS(AUTO_URI, ATTR_START_DESTINATION)

        if (startDestination.isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        // Resolve the startDestination reference to an id value
        // The startDestination is typically in the form "@id/someId" or "@+id/someId"
        val referencedId = resolveIdReference(startDestination) ?: return

        // Check that the referenced id corresponds to a direct child of this navigation element
        var foundChild = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childId = getIdValue(child)
                if (childId != null && childId == referencedId) {
                    foundChild = true
                    break
                }
            }
        }

        if (!foundChild) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Start destination `$startDestination` is not a direct child of this `<navigation>`"
            )
        }
    }

    /**
     * Resolves an ID reference like "@id/foo" or "@+id/foo" to just "foo".
     */
    private fun resolveIdReference(reference: String): String? {
        val trimmed = reference.trim()
        return when {
            trimmed.startsWith("@+id/") -> trimmed.removePrefix("@+id/")
            trimmed.startsWith("@id/") -> trimmed.removePrefix("@id/")
            else -> null
        }
    }

    /**
     * Gets the id value (without the @id/ or @+id/ prefix) from an element's android:id attribute.
     */
    private fun getIdValue(element: Element): String? {
        val androidId = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_ID)
        if (!androidId.isNullOrBlank()) {
            return resolveIdReference(androidId)
        }
        return null
    }
}