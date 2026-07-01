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
        private const val NAVIGATION_NS = "http://schemas.android.com/apk/res-auto"

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

        private fun resolveId(idRef: String): String {
            // Strip @id/, @+id/, @navigation/ etc. prefixes
            val slashIndex = idRef.indexOf('/')
            return if (slashIndex >= 0) {
                idRef.substring(slashIndex + 1)
            } else {
                idRef
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Get the startDestination attribute
        val startDestAttr = element.getAttributeNS(NAVIGATION_NS, ATTR_START_DESTINATION)
            .takeIf { it.isNotEmpty() }
            ?: element.getAttribute(ATTR_START_DESTINATION).takeIf { it.isNotEmpty() }

        if (startDestAttr.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestId = resolveId(startDestAttr)
        if (startDestId.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        // Check that the start destination is a direct child of this navigation element
        val children = element.childNodes
        var found = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val childId = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)
                    .takeIf { it.isNotEmpty() }
                    ?: child.getAttribute(SdkConstants.ATTR_ID).takeIf { it.isNotEmpty() }
                    ?: continue

                val resolvedChildId = resolveId(childId)
                if (resolvedChildId == startDestId) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            val attrNode = element.getAttributeNodeNS(NAVIGATION_NS, ATTR_START_DESTINATION)
                ?: element.getAttributeNode(ATTR_START_DESTINATION)

            val location = if (attrNode != null) {
                context.getValueLocation(attrNode)
            } else {
                context.getNameLocation(element)
            }

            context.report(
                ISSUE,
                element,
                location,
                "Start destination `$startDestId` is not a direct child of this `<navigation>`"
            )
        }
    }
}