package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

@Suppress("UnstableApiUsage")
class StartDestinationDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.NAVIGATION

    override fun getApplicableElements(): Collection<String> = listOf("navigation")

    override fun visitElement(context: XmlContext, element: Element) {
        val startDestination = element.getAttributeNS(AUTO_URI, "startDestination")
        if (startDestination.isBlank()) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "No start destination specified for this navigation graph"
            )
            return
        }

        val destinationId = startDestination.stripIdReference()
        if (destinationId == null) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "Invalid start destination reference: `$startDestination`"
            )
            return
        }

        val hasDirectChild = (0 until element.childNodes.length).any { i ->
            val child = element.childNodes.item(i)
            child.nodeType == Node.ELEMENT_NODE &&
                (child as Element).getAttributeNS(ANDROID_URI, ATTR_ID)
                    .stripIdReference() == destinationId
        }

        if (!hasDirectChild) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "The start destination must be a direct child of this navigation graph"
            )
        }
    }

    private fun String.stripIdReference(): String? = when {
        startsWith("@id/") -> substring(4)
        startsWith("@+id/") -> substring(5)
        startsWith("@android:id/") -> substring(14)
        else -> null
    }

    companion object {
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                All `<navigation>` elements must specify an `app:startDestination` attribute, \
                and the referenced destination must be a direct child of that `<navigation>` element.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}