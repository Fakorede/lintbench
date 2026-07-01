package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String> = ADAPTER_VIEW_TAGS

    override fun visitElement(context: XmlContext, element: Element) {
        if (hasElementChild(element)) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "AdapterView cannot have children in XML"
            )
        }
    }

    private fun hasElementChild(element: Element): Boolean {
        val children = element.childNodes
        for (i in 0 until children.length) {
            if (children.item(i).nodeType == Node.ELEMENT_NODE) {
                return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AdapterViewChildren",
            briefDescription = "AdapterView cannot have children in XML",
            explanation = """
                An `AdapterView` such as a `ListView` must be configured with an adapter in code.
                It cannot contain child views in XML.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val ADAPTER_VIEW_SIMPLE_NAMES = listOf(
            "ListView",
            "GridView",
            "Spinner",
            "ExpandableListView",
            "Gallery",
            "StackView",
            "AdapterViewFlipper"
        )

        private val ADAPTER_VIEW_TAGS: List<String> = ADAPTER_VIEW_SIMPLE_NAMES.flatMap { name ->
            listOf(name, "android.widget.$name")
        }
    }
}