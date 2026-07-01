package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        val ISSUE = Issue.create(
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "startDestination")
        if (startDestAttr == null) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val destValue = startDestAttr.value
        val destId = parseId(destValue) ?: return

        val childNodes = element.childNodes
        var found = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childIdAttr = (child as Element).getAttributeNodeNS(SdkConstants.ANDROID_URI, "id")
                if (childIdAttr != null && parseId(childIdAttr.value) == destId) {
                    found = true
                    break
                }
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                context.getLocation(startDestAttr),
                "Start destination is not a direct child of this <navigation> element"
            )
        }
    }

    private fun parseId(value: String): String? {
        return when {
            value.startsWith("@id/") -> value.removePrefix("@id/")
            value.startsWith("@+id/") -> value.removePrefix("@+id/")
            else -> null
        }
    }
}