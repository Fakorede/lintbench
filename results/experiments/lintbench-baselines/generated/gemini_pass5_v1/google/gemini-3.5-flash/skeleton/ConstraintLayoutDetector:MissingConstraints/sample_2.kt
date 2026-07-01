package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ConstraintLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, \
                and it records the current position with designtime attributes (such as \
                `layout_editor_absoluteX`). These attributes are not applied at \
                runtime, so if you push your layout on a device, the widgets may appear \
                in a different location than shown in the editor. To fix this, make sure \
                a widget has both horizontal and vertical constraints by dragging from \
                the edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

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
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "androidx.constraintlayout.widget.ConstraintLayout",
            "android.support.constraint.ConstraintLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val childNode = childNodes.item(i)
            if (childNode is org.w3c.dom.Element) {
                checkChild(context, childNode)
            }
        }
    }

    private fun checkChild(context: XmlContext, child: org.w3c.dom.Element) {
        val tag = child.tagName
        if (tag == "Guideline" || tag.endsWith(".Guideline") ||
            tag == "Barrier" || tag.endsWith(".Barrier") ||
            tag == "Group" || tag.endsWith(".Group") ||
            tag == "Constraints" || tag.endsWith(".Constraints") ||
            tag == "androidx.constraintlayout.helper.widget.Flow" ||
            tag == "flow" || tag.endsWith(".Flow")
        ) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false
        var hasCircle = false

        val attributes = child.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.localName ?: attr.nodeName ?: continue
            if (name.startsWith("layout_constraint")) {
                if (name == "layout_constraintCircle") {
                    hasCircle = true
                } else if (HORIZONTAL_CONSTRAINTS.contains(name)) {
                    hasHorizontal = true
                } else if (VERTICAL_CONSTRAINTS.contains(name)) {
                    hasVertical = true
                }
            }
        }

        if (hasCircle) {
            return
        }

        val missingHorizontal = !hasHorizontal
        val missingVertical = !hasVertical

        if (missingHorizontal || missingVertical) {
            val message = when {
                missingHorizontal && missingVertical -> {
                    "This view is not constrained. It only has designtime constraints, so it will jump to (0,0) at runtime unless you add constraints"
                }
                missingHorizontal -> {
                    "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint"
                }
                else -> {
                    "This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint"
                }
            }
            context.report(
                ISSUE,
                child,
                context.getNameLocation(child),
                message
            )
        }
    }
}