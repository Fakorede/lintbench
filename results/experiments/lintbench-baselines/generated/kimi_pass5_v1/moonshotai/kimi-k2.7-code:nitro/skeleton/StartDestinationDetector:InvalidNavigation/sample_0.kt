package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val NAVIGATION_TAG = "navigation"
        private const val START_DESTINATION_ATTR = "startDestination"

        private val IMPLEMENTATION = Implementation(
            StartDestinationDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = "All <navigation> elements must specify a start destination, and the start destination must be a direct child of the navigation graph.",
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.NAVIGATION

    override fun getApplicableElements(): Collection<String>? =
        listOf(NAVIGATION_TAG)

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(AUTO_URI, START_DESTINATION_ATTR)
        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified for this navigation graph",
            )
            return
        }

        val startDestination = attr.value
        if (startDestination.isBlank()) {
            context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "No start destination specified for this navigation graph",
            )
            return
        }

        val destinationName = getReferenceName(startDestination)
        if (destinationName == null || !hasChildWithId(element, destinationName)) {
            context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "The start destination must be a direct child of this navigation graph",
            )
        }
    }

    private fun hasChildWithId(element: Element, destinationName: String): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val childIdValue = (child as Element).getAttributeNS(ANDROID_URI, "id")
                if (getReferenceName(childIdValue) == destinationName) {
                    return true
                }
            }
        }
        return false
    }

    private fun getReferenceName(value: String): String? {
        if (!value.startsWith("@")) {
            return null
        }
        var ref = value.substring(1)
        if (ref.startsWith("+")) {
            ref = ref.substring(1)
        }
        val slash = ref.lastIndexOf('/')
        if (slash == -1 || slash == ref.length - 1) {
            return null
        }
        return ref.substring(slash + 1)
    }
}