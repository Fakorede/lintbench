package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = "An AdapterView such as a ListViews must be configured with data from Java code, such as a ListAdapter.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.evaluator.extendsClass(element, "android.widget.AdapterView", false)) {
            return
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                context.report(
                    ISSUE,
                    context.getLocation(element),
                    "AdapterView cannot have children in XML"
                )
                return
            }
            child = child.nextSibling
        }
    }
}