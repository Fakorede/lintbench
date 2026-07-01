package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                checkChild(context, child)
            }
        }
    }

    private fun checkChild(context: XmlContext, child: Element) {
        val tagName = child.tagName
        if (isExcluded(tagName)) return

        var hasHorizontal = false
        var hasVertical = false

        val attributes = child.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) ?: continue
            val localName = attr.localName ?: attr.nodeName.substringAfter(':')
            if (localName.startsWith("layout_constraint")) {
                if (localName == "layout_constraintCircle") {
                    hasHorizontal = true
                    hasVertical = true
                } else if (HORIZONTAL_CONSTRAINTS.contains(localName)) {
                    hasHorizontal = true
                } else if (VERTICAL_CONSTRAINTS.contains(localName)) {
                    hasVertical = true
                }
            }
        }

        if (!hasHorizontal || !hasVertical) {
            val message = when {
                !hasHorizontal && !hasVertical ->
                    "This view is not constrained. It only has designtime constraints, so it will jump to (0,0) at runtime unless you add the constraints"
                !hasHorizontal ->
                    "This view is not constrained horizontally: at runtime it will jump to the left unless you add a constraint"
                else ->
                    "This view is not constrained vertically: at runtime it will jump to the top unless you add a constraint"
            }
            context.report(ISSUE, child, context.getNameLocation(child), message)
        }
    }

    private fun isExcluded(tagName: String): Boolean {
        return tagName == "Guideline" || tagName.endsWith(".Guideline") ||
               tagName == "Barrier" || tagName.endsWith(".Barrier") ||
               tagName == "Group" || tagName.endsWith(".Group") ||
               tagName == "Placeholder" || tagName.endsWith(".Placeholder") ||
               tagName == "androidx.constraintlayout.helper.widget.Flow" ||
               tagName.endsWith(".Flow")
    }

    companion object {
        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf"
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are **not** applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.LAYOUT_RESOURCE_SCOPE
            )
        )
    }
}