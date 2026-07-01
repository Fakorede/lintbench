package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_NAVIGATION
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
            explanation = "All `<navigation>` elements must have a start destination specified, and it must be a direct child of that `<navigation>`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private fun stripId(id: String): String {
            return when {
                id.startsWith("@id/") -> id.substring(4)
                id.startsWith("@+id/") -> id.substring(5)
                else -> id
            }
        }
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDest = element.getAttributeNS(AUTO_URI, ATTR_START_DESTINATION)
        if (startDest.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestId = stripId(startDest)
        if (startDestId.isEmpty()) {
            return
        }

        var found = false
        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                val childIdAttr = child.getAttributeNS(ANDROID_URI, ATTR_ID)
                if (!childIdAttr.isNullOrEmpty()) {
                    val childId = stripId(childIdAttr)
                    if (childId == startDestId) {
                        found = true
                        break
                    }
                }
            }
            child = child.nextSibling
        }

        if (!found) {
            val attributeNode = element.getAttributeNodeNS(AUTO_URI, ATTR_START_DESTINATION)
            val location = if (attributeNode != null) {
                context.getLocation(attributeNode)
            } else {
                context.getNameLocation(element)
            }
            context.report(
                ISSUE,
                element,
                location,
                "The start destination `$startDest` must be a direct child of the `<navigation>` element"
            )
        }
    }
}