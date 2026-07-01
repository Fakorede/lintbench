package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ChildCountDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf("*")

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.isSubclassOf(element, "android.widget.AdapterView")) {
            return
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            if (childNodes.item(i) is Element) {
                context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "AdapterView cannot have children in XML"
                )
                break
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = "An AdapterView such as a ListView must be configured with data from Java code, such as a ListAdapter.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}