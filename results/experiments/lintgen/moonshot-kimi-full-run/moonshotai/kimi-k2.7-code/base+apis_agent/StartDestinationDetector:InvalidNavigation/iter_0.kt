package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class StartDestinationDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(SdkConstants.TAG_NAVIGATION)

    override fun visitElement(context: XmlContext, element: Element) {
        val startDest = element.getStartDestination()
        if (startDest == null || startDest.isBlank()) {
            reportMissing(context, element)
            return
        }

        val hasMatchingChild = element.directChildren().any { child ->
            child.idName() == startDest
        }

        if (!hasMatchingChild) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The `startDestination` value does not match the `android:id` of any direct child of this `<navigation>` element"
            )
        }
    }

    private fun reportMissing(context: XmlContext, element: Element) {
        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "`<navigation>` must specify a `startDestination` attribute that references a direct child destination"
        )
    }

    private fun Element.getStartDestination(): String? {
        val value = getAttributeNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_START_DESTINATION)
        if (value.isNotBlank()) {
            return idName(value)
        }

        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.localName == SdkConstants.ATTR_START_DESTINATION) {
                return idName(attr.nodeValue)
            }
        }

        return null
    }

    private fun Element.idName(): String? {
        val raw = getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID)
        return if (raw.isNotBlank()) idName(raw) else null
    }

    private fun Element.directChildren(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    private fun idName(value: String): String {
        return when {
            value.startsWith(SdkConstants.ID_PREFIX) -> value.substring(SdkConstants.ID_PREFIX.length)
            value.startsWith("@id/") -> value.substring("@id/".length)
            value.startsWith("@+id:") -> value.substring("@+id:".length)
            value.startsWith("@id:") -> value.substring("@id:".length)
            else -> value
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                Every `<navigation>` element must declare a `startDestination` attribute, and the value \
                must match the `android:id` of one of its direct child destinations (for example a \
                `<fragment>`, `<activity>`, or nested `<navigation>`). Without a valid start destination \
                the navigation graph cannot be used at runtime.
                """,
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