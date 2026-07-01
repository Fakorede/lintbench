package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "Invalid navigation start destination",
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

    override fun getApplicableElements(): Collection<String> {
        return listOf("navigation")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "startDestination")
            ?: element.getAttributeNode("app:startDestination")
            ?: element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "startDestination")

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
        val startDestId = extractId(startDestValue)

        if (startDestId == null) {
            context.report(
                ISSUE,
                startDestAttr,
                context.getLocation(startDestAttr),
                "Invalid start destination ID"
            )
            return
        }

        var found = false
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                val idAttr = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI, "id")
                    ?: childElement.getAttributeNode("android:id")
                if (idAttr != null) {
                    val childId = extractId(idAttr.value)
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
                context.getLocation(startDestAttr),
                "Start destination `$startDestValue` must be a direct child of the `<navigation>` element"
            )
        }
    }

    private fun extractId(idValue: String?): String? {
        if (idValue == null) return null
        val index = idValue.lastIndexOf('/')
        return if (index >= 0) idValue.substring(index + 1) else idValue
    }
}