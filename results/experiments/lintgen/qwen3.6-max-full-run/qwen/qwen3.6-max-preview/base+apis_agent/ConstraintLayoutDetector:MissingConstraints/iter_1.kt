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
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : Detector(), XmlScanner {
    companion object {
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas,
                and it records the current position with designtime attributes (such as
                `layout_editor_absoluteX`). These attributes are **not** applied at
                runtime, so if you push your layout on a device, the widgets may appear
                in a different location than shown in the editor. To fix this, make sure
                a widget has both horizontal and vertical constraints by dragging from
                the edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val CONSTRAINT_LAYOUTS = setOf(
            "androidx.constraintlayout.widget.ConstraintLayout",
            "android.support.constraint.ConstraintLayout",
            "ConstraintLayout"
        )

        private val HELPER_VIEWS = setOf(
            "Guideline", "Barrier", "Group", "Placeholder", "Layer", "Flow", "MockView"
        )
    }

    override fun getApplicableElements(): Collection<String>? = CONSTRAINT_LAYOUTS

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                checkChild(context, node as Element)
            }
        }
    }

    private fun checkChild(context: XmlContext, child: Element) {
        val tag = child.tagName
        if (HELPER_VIEWS.any { tag.endsWith(it) }) return
        if (tag == "merge" || tag == "include" || tag == "fragment") return

        var hasHorizontal = false
        var hasVertical = false

        val attributes = child.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val localName = attr.localName ?: attr.nodeName.substringAfter(":")

            if (localName.startsWith("layout_constraint")) {
                if (localName.contains("_toLeftOf") || localName.contains("_toRightOf") ||
                    localName.contains("_toStartOf") || localName.contains("_toEndOf") ||
                    localName.contains("_circle")
                ) {
                    hasHorizontal = true
                }
                if (localName.contains("_toTopOf") || localName.contains("_toBottomOf") ||
                    localName.contains("_toBaselineOf") || localName.contains("_circle")
                ) {
                    hasVertical = true
                }
            }
        }

        val width = child.getAttribute("android:layout_width")
        val height = child.getAttribute("android:layout_height")
        if (width == "match_parent") hasHorizontal = true
        if (height == "match_parent") hasVertical = true

        if (!hasHorizontal || !hasVertical) {
            val missing = buildString {
                if (!hasHorizontal) append("horizontal")
                if (!hasHorizontal && !hasVertical) append(" and ")
                if (!hasVertical) append("vertical")
            }
            context.report(
                ISSUE,
                context.getLocation(child),
                "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the missing $missing constraints"
            )
        }
    }
}