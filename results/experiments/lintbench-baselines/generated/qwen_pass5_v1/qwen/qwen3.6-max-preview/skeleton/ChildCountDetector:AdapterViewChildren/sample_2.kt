package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

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
            explanation = "An AdapterView such as a ListView must be configured with data from Java code, such as a ListAdapter. Adding children in XML will cause runtime exceptions or be ignored.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("*")

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.isViewClass(element, "android.widget.AdapterView")) {
            return
        }

        var hasChildren = false
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            if (childNodes.item(i).nodeType == Node.ELEMENT_NODE) {
                hasChildren = true
                break
            }
        }

        if (hasChildren) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "AdapterView cannot have children in XML"
            )
        }
    }
}