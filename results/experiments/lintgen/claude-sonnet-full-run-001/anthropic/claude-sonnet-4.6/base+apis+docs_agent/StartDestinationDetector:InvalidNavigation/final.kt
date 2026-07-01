package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class StartDestinationDetector : Detector(), XmlScanner {

    companion object {
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

        private const val TAG_NAVIGATION = "navigation"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttributeNS(
            SdkConstants.AUTO_URI,
            SdkConstants.ATTR_START_DESTINATION
        )

        if (startDestination.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        // Extract the id reference value (strip @id/ or @+id/ prefix)
        val destId = startDestination
            .removePrefix("@+id/")
            .removePrefix("@id/")

        // Check that the start destination refers to a direct child of this navigation element
        val childNodes = element.childNodes
        var foundDirectChild = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childId = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)
                    .removePrefix("@+id/")
                    .removePrefix("@id/")
                if (childId == destId) {
                    foundDirectChild = true
                    break
                }
            }
        }

        if (!foundDirectChild) {
            val attr = element.getAttributeNodeNS(
                SdkConstants.AUTO_URI,
                SdkConstants.ATTR_START_DESTINATION
            )
            val location = if (attr != null) {
                context.getValueLocation(attr)
            } else {
                context.getNameLocation(element)
            }
            context.report(
                ISSUE,
                element,
                location,
                "Start destination `$startDestination` is not a direct child of this `<navigation>`"
            )
        }
    }
}