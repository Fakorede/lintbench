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

        // Resolve the start destination reference to an id value
        // startDestination is typically "@id/someId" or "@+id/someId"
        val destId = resolveIdReference(startDestination)

        if (destId == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Invalid start destination: `$startDestination`"
            )
            return
        }

        // Check that the referenced id corresponds to a direct child of this navigation element
        val directChildren = getDirectChildIds(element)

        if (!directChildren.contains(destId)) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Start destination `$startDestination` is not a direct child of this `<navigation>`"
            )
        }
    }

    /**
     * Resolves an id reference like "@id/foo" or "@+id/foo" to just "foo".
     */
    private fun resolveIdReference(ref: String): String? {
        val trimmed = ref.trim()
        return when {
            trimmed.startsWith("@+id/") -> trimmed.removePrefix("@+id/")
            trimmed.startsWith("@id/") -> trimmed.removePrefix("@id/")
            trimmed.startsWith("@android:id/") -> trimmed.removePrefix("@android:id/")
            else -> null
        }
    }

    /**
     * Collects the android:id or app:id values of all direct children of the given element.
     */
    private fun getDirectChildIds(element: Element): Set<String> {
        val ids = mutableSetOf<String>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                // Try android:id first, then auto namespace id
                val androidId = child.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_ID)
                if (androidId.isNotBlank()) {
                    val resolved = resolveIdReference(androidId)
                    if (resolved != null) {
                        ids.add(resolved)
                    }
                }
                // Also check auto URI
                val autoId = child.getAttributeNS(AUTO_URI, ATTR_ID)
                if (autoId.isNotBlank()) {
                    val resolved = resolveIdReference(autoId)
                    if (resolved != null) {
                        ids.add(resolved)
                    }
                }
            }
        }
        return ids
    }
}