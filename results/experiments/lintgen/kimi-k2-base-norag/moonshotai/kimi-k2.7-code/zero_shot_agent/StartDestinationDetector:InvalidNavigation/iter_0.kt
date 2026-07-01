package com.android.tools.lint.checks

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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttributeNS(APP_URI, ATTR_START_DESTINATION)
        if (startDestination.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        val targetId = extractIdName(startDestination)
        if (targetId == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Invalid start destination specified"
            )
            return
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue

            val childElement = child as Element
            val childId = extractIdName(childElement.getAttributeNS(ANDROID_URI, ATTR_ID))
            if (childId == targetId) {
                return
            }
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "Start destination must be a direct child of this `<navigation>`"
        )
    }

    private fun extractIdName(value: String): String? {
        if (value.isBlank()) return null
        val index = value.lastIndexOf('/')
        return if (index != -1 && index < value.length - 1) {
            value.substring(index + 1)
        } else {
            value
        }
    }

    companion object {
        private const val TAG_NAVIGATION = "navigation"
        private const val ATTR_START_DESTINATION = "startDestination"
        private const val ATTR_ID = "id"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val APP_URI = "http://schemas.android.com/apk/res-auto"

        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                Every `<navigation>` element must declare a `app:startDestination` \
                attribute that references the `android:id` of one of its direct children.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}