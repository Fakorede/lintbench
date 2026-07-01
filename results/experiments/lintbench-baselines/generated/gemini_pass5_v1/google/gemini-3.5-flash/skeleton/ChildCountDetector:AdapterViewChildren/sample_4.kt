package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChildCountDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with data \
                from Java code, such as a `ListAdapter`. Defining children in the \
                XML layout is not supported and will result in a runtime exception.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "ListView",
            "GridView",
            "Spinner",
            "Gallery",
            "StackView",
            "AdapterViewFlipper",
            "ExpandableListView",
            "android.widget.ListView",
            "android.widget.GridView",
            "android.widget.Spinner",
            "android.widget.Gallery",
            "android.widget.StackView",
            "android.widget.AdapterViewFlipper",
            "android.widget.ExpandableListView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        var child = element.firstChild
        while (child != null) {
            if (child is Element) {
                val tagName = child.tagName
                if (tagName != "requestFocus" && tagName != "tag") {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "An `AdapterView` such as a `ListView` cannot have children in XML"
                    )
                    break
                }
            }
            child = child.nextSibling
        }
    }
}