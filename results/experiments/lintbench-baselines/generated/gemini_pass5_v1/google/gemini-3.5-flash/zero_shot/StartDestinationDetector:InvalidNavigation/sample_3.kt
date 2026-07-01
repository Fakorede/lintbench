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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "startDestination")
        if (startDestAttr == null || startDestAttr.value.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestVal = stripId(startDestAttr.value)
        if (startDestVal.isEmpty()) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getLocation(startDestAttr),
                "No start destination specified"
            )
            return
        }

        var found = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val idAttr = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)
                if (idAttr != null && stripId(idAttr) == startDestVal) {
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
                "Start destination must be a direct child of the navigation element"
            )
        }
    }

    private fun stripId(id: String): String {
        return when {
            id.startsWith("@+id/") -> id.substring(5)
            id.startsWith("@id/") -> id.substring(4)
            else -> id
        }
    }
}