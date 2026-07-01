package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class StartDestinationDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val startDestAttr = element.getAttributeNodeNS(com.android.SdkConstants.AUTO_URI, "startDestination")
        if (startDestAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val startDestValue = startDestAttr.value
        if (startDestValue.isNullOrEmpty()) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getValueLocation(startDestAttr),
                "No start destination specified"
            )
            return
        }

        val startDestId = extractIdName(startDestValue)

        var found = false
        var child = element.firstChild
        while (child != null) {
            if (child is org.w3c.dom.Element) {
                val childIdAttr = child.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "id")
                if (childIdAttr != null) {
                    val childId = extractIdName(childIdAttr.value)
                    if (childId == startDestId) {
                        found = true
                        break
                    }
                }
            }
            child = child.nextSibling
        }

        if (!found) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getValueLocation(startDestAttr),
                "The start destination must be a direct child of the <navigation> element"
            )
        }
    }

    private fun extractIdName(idValue: String): String {
        return when {
            idValue.startsWith("@id/") -> idValue.substring(4)
            idValue.startsWith("@+id/") -> idValue.substring(5)
            else -> idValue
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All `<navigation>` elements must have a start destination specified, and it must be a direct child of that `<navigation>`.",
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