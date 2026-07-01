package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val APP_URI = "http://schemas.android.com/apk/res-auto"

        private val IMPLEMENTATION = Implementation(
            StartDestinationDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                Every `<navigation>` element must declare a start destination via the
                `app:startDestination` attribute, and the referenced destination must be a
                direct child of that `<navigation>` element (identified by its `android:id`).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 3,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean =
        folderType == com.android.resources.ResourceFolderType.NAVIGATION

    override fun getApplicableElements(): Collection<String>? = listOf("navigation")

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val startDestination = element.getAttributeNS(APP_URI, "startDestination").trim()
        if (startDestination.isEmpty()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified for this `<navigation>` element"
            )
            return
        }

        val destinationName = getResourceName(startDestination)
        val children = element.childNodes

        var found = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
                continue
            }

            val childElement = child as org.w3c.dom.Element
            val childId = childElement.getAttributeNS(ANDROID_URI, "id").trim()
            if (childId.isNotEmpty() && getResourceName(childId) == destinationName) {
                found = true
                break
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The start destination (`$startDestination`) is not a direct child of this `<navigation>` element"
            )
        }
    }

    private fun getResourceName(value: String): String {
        var trimmed = value.trim()
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1)
        }
        if (trimmed.startsWith("+")) {
            trimmed = trimmed.substring(1)
        }
        val slash = trimmed.indexOf('/')
        return if (slash != -1) trimmed.substring(slash + 1) else trimmed
    }
}