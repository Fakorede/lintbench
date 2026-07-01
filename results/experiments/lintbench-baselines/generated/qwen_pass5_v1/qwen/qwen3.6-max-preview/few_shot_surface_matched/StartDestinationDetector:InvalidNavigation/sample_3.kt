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
import org.w3c.dom.Node

class StartDestinationDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNodeNS(AUTO_URI, "startDestination")
        if (startDestAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestValue = startDestAttr.value
        val startDestId = extractIdName(startDestValue) ?: return

        var child: Node? = element.firstChild
        while (child != null) {
            if (child is Element) {
                val childIdAttr = child.getAttributeNodeNS(ANDROID_URI, ATTR_ID)
                if (childIdAttr != null && extractIdName(childIdAttr.value) == startDestId) {
                    return
                }
            }
            child = child.nextSibling
        }

        context.report(
            ISSUE,
            startDestAttr,
            context.getValueLocation(startDestAttr),
            "Start destination must be a direct child of this <navigation> element"
        )
    }

    private fun extractIdName(value: String): String? {
        return when {
            value.startsWith("@id/") -> value.substring(4)
            value.startsWith("@+id/") -> value.substring(5)
            else -> value
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All <navigation> elements must have a start destination specified, and it must be a direct child of that <navigation>.",
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