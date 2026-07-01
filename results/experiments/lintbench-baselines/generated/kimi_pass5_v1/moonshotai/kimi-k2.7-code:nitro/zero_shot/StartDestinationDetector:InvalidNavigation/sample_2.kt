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
import org.w3c.dom.Node

class StartDestinationDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.NAVIGATION

    override fun getApplicableElements(): Collection<String>? =
        listOf(NAVIGATION_TAG)

    override fun visitElement(context: XmlContext, element: Element) {
        val startDest = getAttributeValue(
            element,
            AUTO_URI,
            START_DESTINATION
        )

        if (startDest.isNullOrBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified"
            )
            return
        }

        val target = stripIdReference(startDest)
        val foundDirectChild = (0 until element.childNodes.length).any { i ->
            val child = element.childNodes.item(i)
            child.nodeType == Node.ELEMENT_NODE &&
                stripIdReference(
                    getAttributeValue(
                        child as Element,
                        SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_ID
                    ).orEmpty()
                ) == target
        }

        if (!foundDirectChild) {
            val attr = element.getAttributeNodeNS(AUTO_URI, START_DESTINATION)
            val location = if (attr != null) {
                context.getLocation(attr)
            } else {
                context.getLocation(element)
            }
            context.report(
                ISSUE,
                element,
                location,
                "The specified start destination is not a direct child of this `<navigation>` element"
            )
        }
    }

    private fun getAttributeValue(
        element: Element,
        namespaceUri: String,
        localName: String
    ): String? {
        val attr = element.getAttributeNodeNS(namespaceUri, localName)
        if (attr != null) {
            return attr.value
        }
        val raw = element.getAttribute(localName)
        return if (raw.isNotEmpty()) raw else null
    }

    private fun stripIdReference(value: String): String = when {
        value.startsWith(NEW_ID_PREFIX) -> value.substring(NEW_ID_PREFIX.length)
        value.startsWith(ID_PREFIX) -> value.substring(ID_PREFIX.length)
        value.startsWith(ANDROID_ID_PREFIX) -> value.substring(ANDROID_ID_PREFIX.length)
        value.startsWith("@") && value.contains('/') -> value.substring(value.indexOf('/') + 1)
        else -> value
    }

    companion object {
        private const val NAVIGATION_TAG = "navigation"
        private const val START_DESTINATION = "startDestination"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val NEW_ID_PREFIX = "@+id/"
        private const val ID_PREFIX = "@id/"
        private const val ANDROID_ID_PREFIX = "@android:id/"

        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                All `<navigation>` elements must have a start destination specified,
                and it must reference a direct child of that `<navigation>`.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}