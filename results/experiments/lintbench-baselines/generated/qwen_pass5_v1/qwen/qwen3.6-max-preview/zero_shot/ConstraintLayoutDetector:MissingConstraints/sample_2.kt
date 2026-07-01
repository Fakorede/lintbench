package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        private const val CONSTRAINT_LAYOUT_ANDROIDX = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_SUPPORT = "android.support.constraint.ConstraintLayout"

        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintLeft_toLeftOf", "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf", "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf", "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf", "layout_constraintEnd_toEndOf",
            "layout_constraintCircle"
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintTop_toTopOf", "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf", "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf", "layout_constraintCircle"
        )

        private val IGNORED_TAGS = setOf(
            "include", "merge", "fragment",
            "Guideline", "Barrier", "Group", "Placeholder", "Flow", "Layer", "MotionHelper"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = "The layout editor allows you to place widgets anywhere on the canvas, " +
                    "and it records the current position with designtime attributes (such as " +
                    "`layout_editor_absoluteX`). These attributes are **not** applied at " +
                    "runtime, so if you push your layout on a device, the widgets may appear " +
                    "in a different location than shown in the editor. To fix this, make sure " +
                    "a widget has both horizontal and vertical constraints by dragging from " +
                    "the edge connections.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                setOf(Scope.RESOURCE_FILE_SCOPE)
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(CONSTRAINT_LAYOUT_ANDROIDX, CONSTRAINT_LAYOUT_SUPPORT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val child = node as Element
                checkChild(context, child)
            }
        }
    }

    private fun checkChild(context: XmlContext, child: Element) {
        val simpleTag = child.tagName.substringAfterLast('.')
        if (IGNORED_TAGS.contains(simpleTag)) return

        var hasHorizontal = false
        var hasVertical = false

        val attributes = child.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val rawName = attr.localName ?: attr.nodeName
            val name = if (':' in rawName) rawName.substringAfter(':') else rawName

            if (!hasHorizontal && HORIZONTAL_CONSTRAINTS.contains(name)) {
                hasHorizontal = true
            }
            if (!hasVertical && VERTICAL_CONSTRAINTS.contains(name)) {
                hasVertical = true
            }
            if (hasHorizontal && hasVertical) break
        }

        if (!hasHorizontal || !hasVertical) {
            val missing = mutableListOf<String>()
            if (!hasHorizontal) missing.add("horizontal")
            if (!hasVertical) missing.add("vertical")
            val message = "This view is not constrained. It only has designtime positions, " +
                    "so it will jump to (0,0) at runtime unless you add the missing " +
                    "${missing.joinToString(" and ")} constraints"
            context.report(ISSUE, context.getLocation(child), message)
        }
    }
}