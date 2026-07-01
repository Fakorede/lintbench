package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class StartDestinationDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                All `<navigation>` elements must have a start destination specified, \
                and it must be a direct child of that `<navigation>`.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val TAG_NAVIGATION = "navigation"
        private const val ATTR_START_DESTINATION = "startDestination"
        private const val APP_NS = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_NS = SdkConstants.ANDROID_URI
        private const val NAVIGATION_FOLDER = "navigation"
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Only process files in the navigation resource folder
        val folder = context.file.parentFile?.name ?: return
        if (!folder.startsWith(NAVIGATION_FOLDER)) return

        // Look for startDestination attribute (app: namespace first, then android:, then unprefixed)
        val startDestAttr = element.getAttributeNS(APP_NS, ATTR_START_DESTINATION)
            .takeIf { it.isNotEmpty() }
            ?: element.getAttributeNS(ANDROID_NS, ATTR_START_DESTINATION)
                .takeIf { it.isNotEmpty() }
            ?: element.getAttribute(ATTR_START_DESTINATION)
                .takeIf { it.isNotEmpty() }

        if (startDestAttr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        // Resolve the startDestination reference to an ID name
        val destId = resolveIdName(startDestAttr)
        if (destId == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Invalid start destination `$startDestAttr`"
            )
            return
        }

        // Check that the referenced destination is a direct child of this navigation element
        val children = element.childNodes
        var found = false
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is Element) continue

            // Check android:id attribute
            val childId = child.getAttributeNS(ANDROID_NS, "id")
                .takeIf { it.isNotEmpty() }
                ?: child.getAttribute("android:id")
                    .takeIf { it.isNotEmpty() }
                ?: child.getAttribute("id")
                    .takeIf { it.isNotEmpty() }
                ?: continue

            val childIdName = resolveIdName(childId)
            if (childIdName != null && childIdName == destId) {
                found = true
                break
            }
        }

        if (!found) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Start destination `$startDestAttr` is not a direct child of this `<navigation>`"
            )
        }
    }

    /**
     * Extracts the ID name from a reference like "@id/foo", "@+id/foo".
     * Returns "foo" for all of those, or null if the format is unrecognized.
     */
    private fun resolveIdName(ref: String): String? {
        val slashIndex = ref.indexOf('/')
        if (slashIndex < 0) return null
        val name = ref.substring(slashIndex + 1).trim()
        return name.ifEmpty { null }
    }
}