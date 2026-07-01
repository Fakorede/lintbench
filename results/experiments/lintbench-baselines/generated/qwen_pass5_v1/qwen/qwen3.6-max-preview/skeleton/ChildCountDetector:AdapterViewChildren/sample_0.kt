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
            explanation = "An AdapterView such as a ListView must be configured with data from Java code, such as a ListAdapter. Adding children in XML will cause a runtime exception.",
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = Detector.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.isSubclassOf(element, "android.widget.AdapterView", false)) {
            return
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i) is Element) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`AdapterView` cannot have children in XML"
                )
                break
            }
        }
    }
}