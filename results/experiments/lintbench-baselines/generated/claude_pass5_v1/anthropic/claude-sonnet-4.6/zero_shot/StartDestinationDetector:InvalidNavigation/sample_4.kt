package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_NAVIGATION = "navigation"
        private const val ATTR_START_DESTINATION = "startDestination"
        private const val ATTR_ID = "id"
        private const val APP_NAMESPACE = SdkConstants.AUTO_URI

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
        val startDestAttr = element.getAttributeNS(APP_NAMESPACE, ATTR_START_DESTINATION)

        if (startDestAttr.isNullOrEmpty()) {
            // No start destination specified at all
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        // The startDestination value is a reference like @id/xxx or @+id/xxx
        // Extract the id name from the reference
        val destId = extractIdName(startDestAttr) ?: return

        // Check that the referenced id is a direct child of this navigation element
        val childNodes = element.childNodes
        var found = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childId = getChildId(child)
                if (childId != null && childId == destId) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            val location = context.getValueLocation(
                element.getAttributeNodeNS(APP_NAMESPACE, ATTR_START_DESTINATION)
            )
            context.report(
                ISSUE,
                element,
                location,
                "Start destination `$startDestAttr` is not a direct child of this `<navigation>`"
            )
        }
    }

    /**
     * Extracts the id name from a reference string like @id/name or @+id/name.
     */
    private fun extractIdName(reference: String): String? {
        // Handle @id/name and @+id/name formats
        val idPrefixPattern = Regex("^@\\+?id/(.+)$")
        val matchResult = idPrefixPattern.find(reference.trim())
        return matchResult?.groupValues?.get(1)
    }

    /**
     * Gets the id name from a child element (strips the @+id/ or @id/ prefix if present,
     * but the id attribute in XML is typically just the bare name or @+id/name).
     */
    private fun getChildId(element: Element): String? {
        // Try app namespace first, then android namespace
        var idValue = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_ID)
        if (idValue.isNullOrEmpty()) {
            idValue = element.getAttribute(ATTR_ID)
        }
        if (idValue.isNullOrEmpty()) return null

        // Strip @+id/ or @id/ prefix if present
        return extractIdName(idValue) ?: idValue
    }
}