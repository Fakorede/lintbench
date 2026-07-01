package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_START_DESTINATION
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
import org.w3c.dom.Node

private const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"

class StartDestinationDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): List<String> = listOf(TAG_NAVIGATION)

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttributeNS(APP_NAMESPACE, ATTR_START_DESTINATION)
        if (startDestination.isBlank()) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "No start destination specified"
            )
            return
        }

        val startId = stripIdReference(startDestination)
        if (startId.isBlank()) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "No start destination specified"
            )
            return
        }

        var found = false
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue

            val childElement = child as Element
            val childId = childElement.getAttributeNS(ANDROID_URI, ATTR_ID)
            if (stripIdReference(childId) == startId) {
                found = true
                break
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "The start destination must be a direct child of this <navigation>"
            )
        }
    }

    private fun stripIdReference(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.substringAfterLast('/').removePrefix("@").removePrefix("+")
    }

    companion object {
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                All `<navigation>` elements must declare an `app:startDestination` attribute,
                and the destination it references must be a direct child of that `<navigation>`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}