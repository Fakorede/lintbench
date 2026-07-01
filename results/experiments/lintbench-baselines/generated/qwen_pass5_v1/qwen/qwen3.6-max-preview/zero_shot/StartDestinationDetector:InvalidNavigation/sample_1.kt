package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class StartDestinationDetector : Detector(), Detector.XmlScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All `<navigation>` elements must have a start destination specified, and it must be a direct child of that `<navigation>`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "startDestination")
            ?: element.getAttributeNode("app:startDestination")

        if (startDestAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val destValue = startDestAttr.value
        if (!destValue.contains("/")) return

        val expectedId = destValue.substringAfterLast("/")

        val children = element.childNodes
        var found = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val childId = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)
                if (childId.endsWith("/$expectedId")) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getLocation(startDestAttr),
                "Start destination must be a direct child of this <navigation> element"
            )
        }
    }
}