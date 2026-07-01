package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return ADAPTER_VIEW_TAGS
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (hasElementChildren(element)) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "AdapterView cannot have children in XML"
            )
        }
    }

    private fun hasElementChildren(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                return true
            }
        }
        return false
    }

    companion object {
        private val ADAPTER_VIEW_TAGS = listOf(
            "AdapterView",
            "ListView",
            "GridView",
            "ExpandableListView",
            "Spinner",
            "Gallery",
            "StackView",
            "AdapterViewFlipper",
            "AdapterViewAnimator"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = """
                `AdapterView` subclasses such as `ListView`, `GridView` and `Spinner` \
                cannot have children in XML. They must be configured with data from \
                Java or Kotlin code, such as a `ListAdapter`.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/reference/android/widget/AdapterView.html",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}