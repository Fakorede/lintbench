package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import java.util.EnumSet

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
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

    override fun getApplicableFolderTypes(): EnumSet<ResourceFolderType>? =
        EnumSet.of(ResourceFolderType.NAVIGATION)

    override fun getApplicableElements(): Collection<String>? = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestAttr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "startDestination")
        if (startDestAttr == null || startDestAttr.value.isBlank()) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val expectedId = extractIdName(startDestAttr.value) ?: return

        val children = element.childNodes
        var isDirectChild = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val childId = child.getAttributeNS(SdkConstants.ANDROID_URI, "id")
                if (extractIdName(childId) == expectedId) {
                    isDirectChild = true
                    break
                }
            }
        }

        if (!isDirectChild) {
            context.report(
                ISSUE,
                context.getLocation(startDestAttr),
                "Start destination is not a direct child of this <navigation> element"
            )
        }
    }

    private fun extractIdName(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        return when {
            value.startsWith("@+id/") -> value.substring(5)
            value.startsWith("@id/") -> value.substring(4)
            else -> null
        }
    }
}