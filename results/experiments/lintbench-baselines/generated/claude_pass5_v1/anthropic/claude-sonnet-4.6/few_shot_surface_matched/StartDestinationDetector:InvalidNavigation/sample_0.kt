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
import org.w3c.dom.Element

class StartDestinationDetector : ResourceXmlDetector() {

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

        // Resolve the start destination reference to an id value
        val startDestValue = startDestAttr.value
        // Strip leading @[+]id/ prefix to get the bare id name
        val startDestId = startDestValue
            .removePrefix("@+id/")
            .removePrefix("@id/")

        // Check that the referenced id matches a direct child element
        val childNodes = element.childNodes
        var found = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i) as? Element ?: continue
            val childIdAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_ID)
                ?: child.getAttributeNodeNS(null, ATTR_ID)
                ?: continue
            val childId = childIdAttr.value
                .removePrefix("@+id/")
                .removePrefix("@id/")
            if (childId == startDestId) {
                found = true
                break
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                element,
                context.getValueLocation(startDestAttr),
                "Start destination `$startDestValue` is not a direct child of this `<$TAG_NAVIGATION>` element"
            )
        }
    }

    companion object {
        private const val TAG_NAVIGATION = "navigation"
        private const val ATTR_START_DESTINATION = "startDestination"

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