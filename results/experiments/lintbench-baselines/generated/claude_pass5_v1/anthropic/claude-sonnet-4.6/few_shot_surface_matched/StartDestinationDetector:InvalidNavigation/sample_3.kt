package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils.getFirstSubTag
import org.w3c.dom.Element

class StartDestinationDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNodeNS(AUTO_URI, "startDestination")
            ?: element.getAttributeNodeNS(ANDROID_URI, "startDestination")

        if (startDestAttr == null || startDestAttr.value.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        // The startDestination value is typically a resource reference like @id/someId or @+id/someId
        val startDestValue = startDestAttr.value
        // Extract the id name from the reference (e.g. "@id/foo" or "@+id/foo" -> "foo")
        val startDestId = startDestValue.substringAfterLast('/').trim()

        if (startDestId.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getValueLocation(startDestAttr),
                "No start destination specified"
            )
            return
        }

        // Check that the start destination refers to a direct child of this navigation element
        var child = getFirstSubTag(element)
        var found = false
        while (child != null) {
            val childId = child.getAttributeNS(ANDROID_URI, "id")
            // childId may look like "@+id/foo" or "@id/foo"
            val childIdName = childId.substringAfterLast('/').trim()
            if (childIdName.isNotEmpty() && childIdName == startDestId) {
                found = true
                break
            }
            child = com.android.utils.XmlUtils.getNextTag(child)
        }

        if (!found) {
            context.report(
                ISSUE,
                element,
                context.getValueLocation(startDestAttr),
                "No start destination specified"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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