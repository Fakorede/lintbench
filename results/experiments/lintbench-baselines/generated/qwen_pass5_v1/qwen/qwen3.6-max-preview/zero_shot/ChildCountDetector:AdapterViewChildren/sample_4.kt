package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
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
        @JvmField
        val ISSUE = Issue.create(
            "AdapterViewChildren",
            "AdapterView cannot have children in XML",
            "An AdapterView such as a ListView must be configured with data from Java code, such as a ListAdapter. They cannot have child views defined in XML.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.evaluator.extendsClass(element, "android.widget.AdapterView", false)) {
            return
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "AdapterView cannot have children in XML"
                )
                return
            }
        }
    }
}