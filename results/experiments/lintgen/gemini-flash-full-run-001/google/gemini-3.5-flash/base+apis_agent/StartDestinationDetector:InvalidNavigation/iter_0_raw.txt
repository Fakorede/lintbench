package com.android.tools.lint.checks

import com.android.SdkConstants
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
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_START_DESTINATION)
            ?: element.getAttributeNode(SdkConstants.ATTR_START_DESTINATION)

        if (startDestAttr == null || startDestAttr.value.isNullOrEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestValue = startDestAttr.value
        val startDestId = startDestValue.substringAfter('/')

        if (startDestId.isEmpty()) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getLocation(startDestAttr),
                "Invalid start destination specified"
            )
            return
        }

        var found = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val childIdAttr = child.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)
                    ?: child.getAttributeNode(SdkConstants.ATTR_ID)
                if (childIdAttr != null) {
                    val childId = childIdAttr.value.substringAfter('/')
                    if (childId == startDestId) {
                        found = true
                        break
                    }
                }
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getLocation(startDestAttr),
                "The start destination `$startDestValue` must be a direct child of the `<navigation>` element"
            )
        }
    }
}