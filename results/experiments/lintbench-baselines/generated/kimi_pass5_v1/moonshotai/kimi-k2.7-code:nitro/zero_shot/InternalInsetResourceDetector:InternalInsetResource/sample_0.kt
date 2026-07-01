package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector.XmlScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Text
import java.util.EnumSet

class InternalInsetResourceDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> = listOf(XmlScanner.ALL)

    override fun visitElement(context: XmlContext, element: Element) {
        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as? Attr ?: continue
            checkReference(context, attr.value, context.getValueLocation(attr), attr)
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                val text = child as Text
                checkReference(context, text.data, context.getLocation(text), text)
            }
            child = child.nextSibling
        }
    }

    private fun checkReference(
        context: XmlContext,
        value: String,
        location: Location,
        scope: Node
    ) {
        val match = RESOURCE_REF_REGEX.matchEntire(value.trim()) ?: return
        val packageName = match.groupValues[1]
        if (packageName != "android:" && packageName != "*android:") return

        val name = match.groupValues[2]
        if (name in INSET_RESOURCE_NAMES) {
            context.report(ISSUE, scope, location, MESSAGE)
        }
    }

    companion object {
        private const val MESSAGE = "Using internal inset dimension resource"

        private val RESOURCE_REF_REGEX = Regex("^@(\\\\*?android:)?dimen/(.+)$")

        private val INSET_RESOURCE_NAMES = setOf(
            "status_bar_height",
            "status_bar_height_default",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the \
                relevant insets for your application. The insets are dynamic values that can \
                change while your app is visible, and your app's window may not intersect with \
                the system UI. To get the relevant value for your app and listen to updates, use \
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}