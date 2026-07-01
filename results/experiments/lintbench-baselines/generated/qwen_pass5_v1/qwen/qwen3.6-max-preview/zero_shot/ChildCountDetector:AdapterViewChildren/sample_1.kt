package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "`AdapterView` cannot have children in XML",
            explanation = "An `AdapterView` such as a `ListView` must be configured with data from Java code, " +
                    "such as a `ListAdapter`. Adding child views in XML is not supported and will be ignored.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("*")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!context.isSubclassOf(element, "android.widget.AdapterView")) {
            return
        }

        if (XmlUtils.getSubTags(element).hasNext()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "`AdapterView` cannot have children in XML"
            )
        }
    }
}