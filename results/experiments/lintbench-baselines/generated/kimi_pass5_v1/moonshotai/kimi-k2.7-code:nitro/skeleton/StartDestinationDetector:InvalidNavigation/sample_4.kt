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
import org.w3c.dom.Element

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            StartDestinationDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                Every <navigation> element must declare an app:startDestination attribute
                that references the id of one of its direct child destinations. Without a
                valid start destination, the Navigation component cannot determine which
                screen to display first.
            """,
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val NAVIGATION_TAG = "navigation"
        private const val START_DESTINATION_ATTR = "startDestination"
        private const val ID_ATTR = "id"
        private const val APP_NS = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.NAVIGATION

    override fun getApplicableElements(): Collection<String>? =
        listOf(NAVIGATION_TAG)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName != NAVIGATION_TAG) return

        val startDestination = getAttributeValue(element, APP_NS, START_DESTINATION_ATTR)
        if (startDestination.isBlank()) {
            reportIssue(
                context,
                element,
                "This <navigation> does not specify a start destination"
            )
            return
        }

        val startId = stripIdPrefix(startDestination)
        if (startId.isBlank()) {
            reportIssue(
                context,
                element,
                "The start destination reference is not a valid id"
            )
            return
        }

        val childIds = (0 until element.childNodes.length)
            .map { element.childNodes.item(it) }
            .filterIsInstance<Element>()
            .mapNotNull { getAttributeValue(it, ANDROID_NS, ID_ATTR).takeIf { id -> id.isNotBlank() } }
            .map { stripIdPrefix(it) }
            .toSet()

        if (startId !in childIds) {
            reportIssue(
                context,
                element,
                "The start destination must be a direct child of this <navigation>"
            )
        }
    }

    private fun getAttributeValue(
        element: Element,
        namespaceUri: String,
        localName: String
    ): String {
        var value = element.getAttributeNS(namespaceUri, localName)
        if (value.isNotBlank()) return value

        val prefix = when (namespaceUri) {
            ANDROID_NS -> "android"
            APP_NS -> "app"
            else -> null
        }
        if (prefix != null) {
            value = element.getAttribute("$prefix:$localName")
            if (value.isNotBlank()) return value
        }

        return ""
    }

    private fun stripIdPrefix(value: String): String {
        return when {
            value.startsWith("@+id/") -> value.substring(5)
            value.startsWith("@id/") -> value.substring(4)
            value.startsWith("@android:id/") -> value.substring("@android:id/".length)
            else -> value
        }
    }

    private fun reportIssue(context: XmlContext, element: Element, message: String) {
        context.report(ISSUE, element, context.getLocation(element), message)
    }
}