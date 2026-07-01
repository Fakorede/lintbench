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
        private const val NAVIGATION_TAG = "navigation"
        private const val APP_NS = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val ATTR_START_DESTINATION = "startDestination"
        private const val ATTR_ID = "id"

        private val IMPLEMENTATION = Implementation(
            StartDestinationDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                Every <navigation> graph must declare a start destination using
                app:startDestination. The value must reference the android:id of one of
                the direct children of the <navigation> element. A missing or invalid
                start destination means the navigation graph cannot be initialized at
                runtime.
            """.trimIndent(),
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
        if (element.tagName != NAVIGATION_TAG) return

        val startDest = element.getAttributeNodeNS(APP_NS, ATTR_START_DESTINATION)
        if (startDest == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "No start destination specified for this <navigation>."
            )
            return
        }

        val destination = startDest.value.stripIdReference()
        if (destination.isEmpty()) {
            context.report(
                ISSUE,
                startDest,
                context.getLocation(startDest),
                "The start destination is empty."
            )
            return
        }

        val children = element.getElementsByTagName("*")
        var found = false
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.parentNode != element) continue

            val childId = child.getAttributeNS(ANDROID_NS, ATTR_ID).stripIdReference()
            if (childId == destination) {
                found = true
                break
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                startDest,
                context.getLocation(startDest),
                "The start destination \"$destination\" is not a direct child of this <navigation>."
            )
        }
    }

    private fun String.stripIdReference(): String {
        return when {
            startsWith("@+id/") -> substring(5)
            startsWith("@id/") -> substring(4)
            startsWith("@+android:id/") -> substringAfterLast("/")
            startsWith("@android:id/") -> substringAfterLast("/")
            else -> this
        }
    }
}