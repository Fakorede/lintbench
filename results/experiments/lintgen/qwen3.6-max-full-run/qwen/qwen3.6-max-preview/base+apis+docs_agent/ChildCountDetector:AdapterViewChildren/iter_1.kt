package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children",
            explanation = "AdapterView such as ListView cannot have children in XML. " +
                "They must be configured with data from Java code using an Adapter.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.isSubclassOf(element, "android.widget.AdapterView")) {
            val children = element.childNodes
            for (i in 0 until children.length) {
                if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "AdapterView cannot have children"
                    )
                    break
                }
            }
        }
    }
}