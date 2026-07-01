package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

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
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val ATTR_LEFT_TO_LEFT = "layout_constraintLeft_toLeftOf"
        private const val ATTR_LEFT_TO_RIGHT = "layout_constraintLeft_toRightOf"
        private const val ATTR_RIGHT_TO_LEFT = "layout_constraintRight_toLeftOf"
        private const val ATTR_RIGHT_TO_RIGHT = "layout_constraintRight_toRightOf"
        private const val ATTR_START_TO_END = "layout_constraintStart_toEndOf"
        private const val ATTR_START_TO_START = "layout_constraintStart_toStartOf"
        private const val ATTR_END_TO_START = "layout_constraintEnd_toStartOf"
        private const val ATTR_END_TO_END = "layout_constraintEnd_toEndOf"

        private const val ATTR_TOP_TO_TOP = "layout_constraintTop_toTopOf"
        private const val ATTR_TOP_TO_BOTTOM = "layout_constraintTop_toBottomOf"
        private const val ATTR_BOTTOM_TO_TOP = "layout_constraintBottom_toTopOf"
        private const val ATTR_BOTTOM_TO_BOTTOM = "layout_constraintBottom_toBottomOf"
        private const val ATTR_BASELINE_TO_BASELINE = "layout_constraintBaseline_toBaselineOf"

        private val HORIZONTAL_CONSTRAINTS = setOf(
            ATTR_LEFT_TO_LEFT, ATTR_LEFT_TO_RIGHT,
            ATTR_RIGHT_TO_LEFT, ATTR_RIGHT_TO_RIGHT,
            ATTR_START_TO_END, ATTR_START_TO_START,
            ATTR_END_TO_START, ATTR_END_TO_END
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            ATTR_TOP_TO_TOP, ATTR_TOP_TO_BOTTOM,
            ATTR_BOTTOM_TO_TOP, ATTR_BOTTOM_TO_BOTTOM,
            ATTR_BASELINE_TO_BASELINE
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return XmlScanner.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName
        if (parentTag != "androidx.constraintlayout.widget.ConstraintLayout" &&
            parentTag != "android.support.constraint.ConstraintLayout"
        ) {
            return
        }

        val tag = element.tagName
        if (tag == "guideline" || tag == "Guideline" ||
            tag == "barrier" || tag == "Barrier" ||
            tag == "group" || tag == "Group" ||
            tag == "androidx.constraintlayout.widget.Guideline" ||
            tag == "android.support.constraint.Guideline" ||
            tag == "androidx.constraintlayout.widget.Barrier" ||
            tag == "android.support.constraint.Barrier" ||
            tag == "androidx.constraintlayout.widget.Group" ||
            tag == "android.support.constraint.Group" ||
            tag == "androidx.constraintlayout.widget.ConstraintHelper" ||
            tag.endsWith(".Guideline") ||
            tag.endsWith(".Barrier") ||
            tag.endsWith(".Group") ||
            tag.endsWith(".Placeholder")
        ) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val namespace = attr.namespaceURI
            if (namespace == SdkConstants.AUTO_URI) {
                val localName = attr.localName ?: continue
                if (HORIZONTAL_CONSTRAINTS.contains(localName)) {
                    hasHorizontal = true
                } else if (VERTICAL_CONSTRAINTS.contains(localName)) {
                    hasVertical = true
                }
            }
        }

        if (!hasHorizontal || !hasVertical) {
            val missing = when {
                !hasHorizontal && !hasVertical -> "horizontal and vertical"
                !hasHorizontal -> "horizontal"
                else -> "vertical"
            }
            val message = "This view is not constrained. It only has designtime layout constraints. " +
                    "It should have at least one $missing constraint."
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                message
            )
        }
    }
}