package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
private const val LEGACY_CONSTRAINT_LAYOUT = "android.support.constraint.ConstraintLayout"
private const val CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout"

private val HORIZONTAL_CONSTRAINTS = listOf(
    "layout_constraintLeft_toLeftOf",
    "layout_constraintLeft_toRightOf",
    "layout_constraintRight_toLeftOf",
    "layout_constraintRight_toRightOf",
    "layout_constraintStart_toStartOf",
    "layout_constraintStart_toEndOf",
    "layout_constraintEnd_toStartOf",
    "layout_constraintEnd_toEndOf",
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

private val HELPER_WIDGET_NAMES = listOf(
    "Guideline",
    "Group",
    "Barrier",
    "Placeholder",
    "Flow",
    "Layer"
)

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
                The layout editor lets you place widgets anywhere on the canvas and records their
                positions with designtime attributes such as `layout_editor_absoluteX`. These
                attributes are not applied at runtime, so the widget may appear in a different
                location on a device. To fix this, make sure every child view inside a
                ConstraintLayout has both horizontal and vertical constraints (for example,
                `app:layout_constraintStart_toStartOf` and `app:layout_constraintTop_toTopOf`).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(LEGACY_CONSTRAINT_LAYOUT, CONSTRAINT_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes ?: return
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (isHelperWidget(child)) continue

            val hasHorizontal = HORIZONTAL_CONSTRAINTS.any {
                child.hasAttributeNS(AUTO_URI, it)
            }
            val hasVertical = VERTICAL_CONSTRAINTS.any {
                child.hasAttributeNS(AUTO_URI, it)
            }

            when {
                !hasHorizontal && !hasVertical -> {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "This view is not constrained horizontally or vertically. " +
                            "At runtime it will jump to the top-left unless constraints are added."
                    )
                }
                !hasHorizontal -> {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "This view is not constrained horizontally. " +
                            "At runtime it will jump to the left unless a horizontal constraint is added."
                    )
                }
                !hasVertical -> {
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "This view is not constrained vertically. " +
                            "At runtime it will jump to the top unless a vertical constraint is added."
                    )
                }
            }
        }
    }

    private fun isHelperWidget(element: Element): Boolean {
        val tag = element.tagName
        val cls = element.getAttribute("class")
        return HELPER_WIDGET_NAMES.any { name ->
            tag == name || tag.endsWith(".$name") ||
                cls == name || cls.endsWith(".$name")
        }
    }
}