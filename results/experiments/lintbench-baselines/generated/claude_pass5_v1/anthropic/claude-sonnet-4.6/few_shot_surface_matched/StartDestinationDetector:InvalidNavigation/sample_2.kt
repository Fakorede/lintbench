package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class StartDestinationDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNodeNS(AUTO_URI, ATTR_START_DESTINATION)
            ?: element.getAttributeNodeNS(ANDROID_URI, ATTR_START_DESTINATION)

        if (startDestAttr == null || startDestAttr.value.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        // The start destination value is typically a resource reference like @id/someFragment
        // We need to resolve it to an ID and check that it's a direct child of this navigation element
        val startDestValue = startDestAttr.value

        // Extract the id name from the reference (e.g. "@id/foo" or "@+id/foo" -> "foo")
        val idName = extractIdName(startDestValue) ?: return

        // Check if any direct child has this ID
        val childNodes = element.childNodes
        var foundDirectChild = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i) as? Element ?: continue
            val childIdAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_ID) ?: continue
            val childIdName = extractIdName(childIdAttr.value) ?: continue
            if (childIdName == idName) {
                foundDirectChild = true
                break
            }
        }

        if (!foundDirectChild) {
            context.report(
                ISSUE,
                element,
                context.getValueLocation(startDestAttr),
                "Start destination `$startDestValue` is not a direct child of this `<$TAG_NAVIGATION>`"
            )
        }
    }

    private fun extractIdName(reference: String): String? {
        // Handles @id/foo, @+id/foo, @android:id/foo, etc.
        val slashIndex = reference.lastIndexOf('/')
        if (slashIndex < 0) return null
        val name = reference.substring(slashIndex + 1).trim()
        return if (name.isNotEmpty()) name else null
    }

    companion object {
        private const val TAG_NAVIGATION = "navigation"
        private const val ATTR_START_DESTINATION = "startDestination"

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
            severity = Severity.WARNING,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}