package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

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
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are not applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor.

                To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val HORIZONTAL_CONSTRAINTS = listOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf"
        )

        private val VERTICAL_CONSTRAINTS = listOf(
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

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                checkChild(context, node)
            }
        }
    }

    private fun checkChild(context: XmlContext, child: Element) {
        val tagName = child.tagName
        val simpleName = tagName.substringAfterLast('.')
        if (simpleName.isEmpty() || !simpleName[0].isUpperCase()) {
            return
        }

        if (isHelper(simpleName)) {
            return
        }

        var hasHorizontal = false
        for (attr in HORIZONTAL_CONSTRAINTS) {
            if (child.hasAttributeNS("http://schemas.android.com/apk/res-auto", attr) ||
                child.hasAttribute("app:$attr")
            ) {
                hasHorizontal = true
                break
            }
        }

        var hasVertical = false
        for (attr in VERTICAL_CONSTRAINTS) {
            if (child.hasAttributeNS("http://schemas.android.com/apk/res-auto", attr) ||
                child.hasAttribute("app:$attr")
            ) {
                hasVertical = true
                break
            }
        }

        if (!hasHorizontal || !hasVertical) {
            val message = when {
                !hasHorizontal && !hasVertical ->
                    "This view is not constrained. At runtime it will jump to (0,0)."
                !hasHorizontal ->
                    "This view is not constrained horizontally. At runtime it will jump to the left."
                else ->
                    "This view is not constrained vertically. At runtime it will jump to the top."
            }
            context.report(
                ISSUE,
                child,
                context.getNameLocation(child),
                message
            )
        }
    }

    private fun isHelper(simpleName: String): Boolean {
        return simpleName == "Guideline" ||
                simpleName == "Barrier" ||
                simpleName == "Group" ||
                simpleName == "Placeholder" ||
                simpleName == "Flow" ||
                simpleName == "Layer"
    }
}