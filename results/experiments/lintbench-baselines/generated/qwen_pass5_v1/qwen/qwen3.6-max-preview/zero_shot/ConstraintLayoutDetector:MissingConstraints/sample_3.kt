package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ConstraintLayoutDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentElement ?: return
        val parentTag = parent.tagName
        if (parentTag != CONSTRAINT_LAYOUT && parentTag != CONSTRAINT_LAYOUT_ANDROIDX) {
            return
        }

        val tag = element.tagName
        if (isHelperView(tag)) return

        var hasHorizontal = false
        var hasVertical = false

        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as Attr
            val name = attr.localName ?: attr.name
            if (name == CIRCLE_CONSTRAINT) {
                hasHorizontal = true
                hasVertical = true
                break
            }
            if (!hasHorizontal && HORIZONTAL_CONSTRAINTS.contains(name)) hasHorizontal = true
            if (!hasVertical && VERTICAL_CONSTRAINTS.contains(name)) hasVertical = true
        }

        if (hasHorizontal && hasVertical) return

        val message = buildString {
            append("This view is not constrained")
            if (!hasHorizontal && !hasVertical) {
                append(" horizontally and vertically")
            } else if (!hasHorizontal) {
                append(" horizontally")
            } else {
                append(" vertically")
            }
            append(": at runtime it will jump to the top-left unless constraints are provided")
        }

        context.report(ISSUE, element, context.getLocation(element), message)
    }

    private fun isHelperView(tag: String): Boolean {
        return tag == "Guideline" || tag == "androidx.constraintlayout.widget.Guideline" ||
                tag == "Barrier" || tag == "androidx.constraintlayout.widget.Barrier" ||
                tag == "Group" || tag == "androidx.constraintlayout.widget.Group" ||
                tag == "Placeholder" || tag == "androidx.constraintlayout.widget.Placeholder" ||
                tag == "Layer" || tag == "androidx.constraintlayout.widget.Layer" ||
                tag == "Flow" || tag == "androidx.constraintlayout.helper.widget.Flow" ||
                tag == "MockView" || tag == "androidx.constraintlayout.utils.widget.MockView" ||
                tag == "ImageFilterView" || tag == "androidx.constraintlayout.utils.widget.ImageFilterView"
    }

    companion object {
        private const val CONSTRAINT_LAYOUT = "ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_ANDROIDX = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CIRCLE_CONSTRAINT = "layout_constraintCircle"

        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintLeft_toLeftOf", "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf", "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf", "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf", "layout_constraintEnd_toEndOf"
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintTop_toTopOf", "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf", "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf", "layout_constraintBaseline_toTopOf",
            "layout_constraintBaseline_toBottomOf"
        )

        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, \
                and it records the current position with designtime attributes (such as \
                `layout_editor_absoluteX`). These attributes are **not** applied at \
                runtime, so if you push your layout on a device, the widgets may appear \
                in a different location than shown in the editor. To fix this, make sure \
                a widget has both horizontal and vertical constraints by dragging from \
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
    }
}