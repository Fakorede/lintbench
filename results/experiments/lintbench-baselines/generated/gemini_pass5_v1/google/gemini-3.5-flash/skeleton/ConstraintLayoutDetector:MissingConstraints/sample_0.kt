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
                The layout editor allows you to place widgets anywhere on the canvas, and it records the \
                current position with designtime attributes (such as `layout_editor_absoluteX`). These \
                attributes are not applied at runtime, so if you push your layout on a device, the \
                widgets may appear in a different location than shown in the editor. To fix this, make \
                sure a widget has both horizontal and vertical constraints by dragging from the edge \
                connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val tagName = child.tagName
                if (tagName == "requestFocus" || tagName == "tag") {
                    continue
                }

                if (tagName == "Guideline" || tagName.endsWith(".Guideline") ||
                    tagName == "Barrier" || tagName.endsWith(".Barrier") ||
                    tagName == "Group" || tagName.endsWith(".Group") ||
                    tagName == "Placeholder" || tagName.endsWith(".Placeholder") ||
                    tagName == "androidx.constraintlayout.helper.widget.Flow"
                ) {
                    continue
                }

                val hasHorizontal = hasAttribute(child, "layout_constraintLeft_toLeftOf") ||
                        hasAttribute(child, "layout_constraintLeft_toRightOf") ||
                        hasAttribute(child, "layout_constraintRight_toLeftOf") ||
                        hasAttribute(child, "layout_constraintRight_toRightOf") ||
                        hasAttribute(child, "layout_constraintStart_toStartOf") ||
                        hasAttribute(child, "layout_constraintStart_toEndOf") ||
                        hasAttribute(child, "layout_constraintEnd_toStartOf") ||
                        hasAttribute(child, "layout_constraintEnd_toEndOf")

                val hasVertical = hasAttribute(child, "layout_constraintTop_toTopOf") ||
                        hasAttribute(child, "layout_constraintTop_toBottomOf") ||
                        hasAttribute(child, "layout_constraintBottom_toTopOf") ||
                        hasAttribute(child, "layout_constraintBottom_toBottomOf") ||
                        hasAttribute(child, "layout_constraintBaseline_toBaselineOf")

                if (!hasHorizontal && !hasVertical) {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "This view is not constrained. It only has designtime constraints, so it will jump to (0,0) at runtime unless you add the constraints"
                    )
                } else if (!hasHorizontal) {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "This view is not constrained horizontally: at runtime it will jump to the left"
                    )
                } else if (!hasVertical) {
                    context.report(
                        ISSUE,
                        child,
                        context.getNameLocation(child),
                        "This view is not constrained vertically: at runtime it will jump to the top"
                    )
                }
            }
        }
    }

    private fun hasAttribute(element: Element, name: String): Boolean {
        return element.hasAttributeNS("http://schemas.android.com/apk/res-auto", name)
    }
}