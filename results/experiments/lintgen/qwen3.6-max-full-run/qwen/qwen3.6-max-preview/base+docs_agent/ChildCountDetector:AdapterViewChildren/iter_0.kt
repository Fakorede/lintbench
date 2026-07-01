package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.isViewClass(element, "android.widget.AdapterView")) {
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    context.report(
                        ISSUE,
                        context.getLocation(child),
                        "AdapterView cannot have children in XML"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = "AdapterViews such as ListViews must be configured with data from Java code, such as a ListAdapter.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}