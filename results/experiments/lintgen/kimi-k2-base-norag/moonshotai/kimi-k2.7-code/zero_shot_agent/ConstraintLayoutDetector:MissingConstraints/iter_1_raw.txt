package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class ConstraintLayoutDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String> = listOf(
        CONSTRAINT_LAYOUT_ANDROIDX,
        CONSTRAINT_LAYOUT_SUPPORT
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName in HELPER_TAGS) continue

            val hasHorizontal = HORIZONTAL_CONSTRAINTS.any { child.hasAttributeNS(AUTO_URI, it) }
            val hasVertical = VERTICAL_CONSTRAINTS.any { child.hasAttributeNS(AUTO_URI, it) }

            if (!hasHorizontal) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "This view is not constrained horizontally. " +
                            "Add a constraint from the left/right or start/end edges."
                )
            }
            if (!hasVertical) {
                context.report(
                    ISSUE,
                    child,
                    context.getLocation(child),
                    "This view is not constrained vertically. " +
                            "Add a constraint from the top/bottom edges or baseline."
                )
            }
        }
    }

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        private const val CONSTRAINT_LAYOUT_ANDROIDX =
            "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_SUPPORT =
            "android.support.constraint.ConstraintLayout"

        private val HORIZONTAL_CONSTRAINTS = listOf(
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintCircle"
        )

        private val VERTICAL_CONSTRAINTS = listOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintCircle"
        )

        private val HELPER_TAGS = listOf(
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Guideline",
            "androidx.constraintlayout.widget.Barrier",
            "android.support.constraint.Barrier",
            "androidx.constraintlayout.widget.Group",
            "android.support.constraint.Group",
            "androidx.constraintlayout.widget.Placeholder",
            "android.support.constraint.Placeholder"
        )

        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records
                the current position with designtime attributes (such as `layout_editor_absoluteX`).
                These attributes are not applied at runtime, so if you push your layout on a device,
                the widgets may appear in a different location than shown in the editor. To fix this,
                make sure a widget has both horizontal and vertical constraints by dragging from the
                edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}