package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.ID_PREFIX
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ARGUMENT
import com.android.SdkConstants.TAG_DEEP_LINK
import com.android.SdkConstants.TAG_INCLUDE
import com.android.SdkConstants.TAG_NAVIGATION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class StartDestinationDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_NAVIGATION)

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttributeNS(AUTO_URI, ATTR_START_DESTINATION)
        if (startDestination.isBlank()) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "No start destination specified"
            )
            return
        }

        val startId = stripIdPrefix(startDestination)
        if (startId.isBlank()) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "No start destination specified"
            )
            return
        }

        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val tagName = childElement.tagName
                if (tagName == TAG_INCLUDE ||
                    tagName == TAG_ACTION ||
                    tagName == TAG_ARGUMENT ||
                    tagName == TAG_DEEP_LINK
                ) {
                    child = child.nextSibling
                    continue
                }

                val id = childElement.getAttributeNS(ANDROID_URI, ATTR_ID)
                if (stripIdPrefix(id) == startId) {
                    return
                }
            }
            child = child.nextSibling
        }

        context.report(
            ISSUE,
            context.getElementLocation(element),
            "The start destination must be a direct child of the navigation graph"
        )
    }

    private fun stripIdPrefix(value: String): String {
        var s = value
        val index = s.lastIndexOf('/')
        if (index != -1 && index + 1 < s.length) {
            s = s.substring(index + 1)
        }
        if (s.startsWith(NEW_ID_PREFIX)) {
            s = s.substring(NEW_ID_PREFIX.length)
        } else if (s.startsWith(ID_PREFIX)) {
            s = s.substring(ID_PREFIX.length)
        }
        return s
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                All `<navigation>` elements must declare an `app:startDestination` attribute,
                and the destination it references must be a direct child of that `<navigation>`.
            """.trimIndent(),
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