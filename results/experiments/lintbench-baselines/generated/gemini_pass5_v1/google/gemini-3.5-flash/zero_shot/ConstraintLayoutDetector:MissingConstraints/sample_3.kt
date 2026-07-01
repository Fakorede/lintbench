package com.android.tools.lint.checks

import com.android.SdkConstants
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
        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
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

        private val CONSTRAINT_LAYOUTS = setOf(
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )

        private val IGNORED_TAGS = setOf(
            "Guideline",
            "android.support.constraint.Guideline",
            "androidx.constraintlayout.widget.Guideline",
            "Barrier",
            "android.support.constraint.Barrier",
            "androidx.constraintlayout.widget.Barrier",
            "Group",
            "android.support.constraint.Group",
            "androidx.constraintlayout.widget.Group",
            "androidx.constraintlayout.widget.Placeholder",
            "Placeholder",
            "androidx.constraintlayout.helper.widget.Flow",
            "Flow"
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
            "layout_baseline_toBaselineOf"
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return CONSTRAINT_LAYOUTS
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val childNode = childNodes.item(i)
            if (childNode.nodeType == Node.ELEMENT_NODE) {
                val child = childNode as Element
                val tagName = child.tagName
                if (IGNORED_TAGS.contains(tagName)) {
                    continue
                }

                if (child.hasAttributeNS(SdkConstants.AUTO_URI, "layout_constraintCircle")) {
                    continue
                }

                var hasHorizontal = false
                for (attr in HORIZONTAL_CONSTRAINTS) {
                    if (child.hasAttributeNS(SdkConstants.AUTO_URI, attr)) {
                        hasHorizontal = true
                        break
                    }
                }

                var hasVertical = false
                for (attr in VERTICAL_CONSTRAINTS) {
                    if (child.hasAttributeNS(SdkConstants.AUTO_URI, attr)) {
                        hasVertical = true
                        break
                    }
                }

                if (!hasHorizontal || !hasVertical) {
                    val message = when {
                        !hasHorizontal && !hasVertical ->
                            "This view is not constrained. It only has designtime constraints, so it will jump to (0,0) at runtime unless you add the constraints"
                        !hasHorizontal ->
                            "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint"
                        else ->
                            "This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint"
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
    }
}