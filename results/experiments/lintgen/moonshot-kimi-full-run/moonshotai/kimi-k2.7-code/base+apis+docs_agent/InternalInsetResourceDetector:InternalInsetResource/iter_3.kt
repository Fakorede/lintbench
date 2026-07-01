package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.jetbrains.uast.UElement
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            checkReference(context, attr, attr.value)
        }

        val first = element.firstChild
        val last = element.lastChild
        if (first != null && first === last && first.nodeType == Node.TEXT_NODE) {
            checkReference(context, element, first.nodeValue)
        }
    }

    override fun appliesToResourceRefs(): Boolean = true

    override fun visitResourceReference(
        context: JavaContext,
        node: UElement,
        type: ResourceType,
        name: String,
        isFramework: Boolean
    ) {
        if (type == ResourceType.DIMEN && isFramework && name in INSET_DIMENS) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE
            )
        }
    }

    private fun checkReference(context: XmlContext, node: Node, value: String?) {
        if (value.isNullOrEmpty()) return
        val match = RESOURCE_REF_REGEX.matchEntire(value) ?: return
        if (match.groupValues[1] == "android" &&
            match.groupValues[2] == "dimen" &&
            match.groupValues[3] in INSET_DIMENS
        ) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                MESSAGE
            )
        }
    }

    companion object {
        private val INSET_DIMENS = setOf(
            "status_bar_height",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width"
        )

        private val RESOURCE_REF_REGEX = "^[@?]\\*?(\\w+):(\\w+)/(\\w+)$".toRegex()

        private const val MESSAGE = "Using internal inset dimension resource"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "InternalInsetResource",
            briefDescription = MESSAGE,
            explanation = "The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}