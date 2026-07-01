package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "AdapterViewChildren",
            "AdapterView cannot have children in XML",
            "An AdapterView such as a ListView must be configured with data from Java code, " +
                    "such as a ListAdapter.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.evaluator.isSubclassOf(element, "android.widget.AdapterView", false)) {
            var child = element.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "AdapterView cannot have children in XML"
                    )
                    return
                }
                child = child.nextSibling
            }
        }
    }
}