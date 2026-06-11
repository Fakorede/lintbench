package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class ChildCountDetector : Detector(), XmlScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ScrollViewChildCount",
            briefDescription = "ScrollView can have only one child",
            explanation = """
                A ScrollView can only have one direct child widget. If you want to include more children, wrap them in a container layout such as LinearLayout or RelativeLayout.
            """,
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
        return listOf("ScrollView", "NestedScrollView")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        var childCount = 0

        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                childCount++
            }
        }

        if (childCount > 1) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "ScrollView can only have one direct child. Consider wrapping children in a container layout."
            )
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    // Other methods are not required for this detector.
}