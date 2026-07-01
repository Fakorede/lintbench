package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ChildCountDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> = listOf(
        SCROLL_VIEW,
        NESTED_SCROLL_VIEW,
        SUPPORT_NESTED_SCROLL_VIEW
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val directChildCount = (0 until element.childNodes.length).count {
            element.childNodes.item(it).nodeType == Node.ELEMENT_NODE
        }

        if (directChildCount > 1) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A ScrollView can only contain one direct child; wrap children in a container layout."
            )
        }
    }

    companion object {
        private const val SCROLL_VIEW = "ScrollView"
        private const val NESTED_SCROLL_VIEW = "androidx.core.widget.NestedScrollView"
        private const val SUPPORT_NESTED_SCROLL_VIEW = "android.support.v4.widget.NestedScrollView"

        val ISSUE = Issue.create(
            id = "ScrollViewCount",
            briefDescription = "ScrollView has too many children",
            explanation = """
                A ScrollView can only have one direct child. If you want more widgets inside it, wrap them in a container layout such as LinearLayout, FrameLayout, or ConstraintLayout.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ChildCountDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}