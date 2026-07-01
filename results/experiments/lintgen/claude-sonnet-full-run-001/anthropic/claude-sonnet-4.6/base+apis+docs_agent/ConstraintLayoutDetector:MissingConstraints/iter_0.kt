package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X
import com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Checks for widgets inside a ConstraintLayout that are missing
 * horizontal or vertical constraints.
 */
class ConstraintLayoutDetector : LayoutDetector() {

    companion object {
        private const val CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout"
        private const val CONSTRAINT_LAYOUT_LEGACY = "android.support.constraint.ConstraintLayout"

        // Horizontal constraint attributes
        private val HORIZONTAL_CONSTRAINT_ATTRS = setOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            "layout_constraintHorizontal_bias",
            "layout_constraintLeft_creator",
            "layout_constraintRight_creator"
        )

        // Vertical constraint attributes
        private val VERTICAL_CONSTRAINT_ATTRS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintTop_creator",
            "layout_constraintBottom_creator"
        )

        // Attributes that indicate the widget is constrained by a chain or barrier
        private val CHAIN_ATTRS = setOf(
            "layout_constraintHorizontal_chainStyle",
            "layout_constraintVertical_chainStyle",
            "layout_constraintHorizontal_weight",
            "layout_constraintVertical_weight"
        )

        // Tags that are not regular views and should be skipped
        private val SKIP_TAGS = setOf(
            "Guideline",
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Guideline",
            "Barrier",
            "androidx.constraintlayout.widget.Barrier",
            "android.support.constraint.Barrier",
            "Group",
            "androidx.constraintlayout.widget.Group",
            "android.support.constraint.Group",
            "requestFocus",
            "include",
            "merge",
            "tag",
            "data"
        )

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

        private fun isConstraintLayout(element: Element): Boolean {
            val tag = element.tagName
            return tag == CONSTRAINT_LAYOUT || tag == CONSTRAINT_LAYOUT_LEGACY
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(CONSTRAINT_LAYOUT, CONSTRAINT_LAYOUT_LEGACY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (!isConstraintLayout(element)) return

        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node !is Element) continue

            val child = node
            val tag = child.tagName

            // Skip non-view elements
            if (SKIP_TAGS.contains(tag)) continue

            // Skip views with visibility="gone" set via tools (they may be placeholders)
            // Actually we should still check them, but skip pure layout helpers

            checkChildConstraints(context, child)
        }
    }

    private fun checkChildConstraints(context: XmlContext, child: Element) {
        // Check if this child has an id of "@+id/..." - if it's a guideline or barrier skip it
        val tag = child.tagName
        if (SKIP_TAGS.any { it.equals(tag, ignoreCase = false) || tag.endsWith(it) }) return

        var hasHorizontalConstraint = false
        var hasVerticalConstraint = false
        var hasAbsoluteX = false
        var hasAbsoluteY = false

        val attrs = child.attributes
        for (j in 0 until attrs.length) {
            val attr = attrs.item(j)
            val localName = attr.localName ?: attr.nodeName ?: continue
            val uri = attr.namespaceURI

            // Check for absolute position attributes (design-time only)
            if (uri == TOOLS_URI) {
                when (localName) {
                    ATTR_LAYOUT_EDITOR_ABSOLUTE_X -> hasAbsoluteX = true
                    ATTR_LAYOUT_EDITOR_ABSOLUTE_Y -> hasAbsoluteY = true
                }
            }

            // Check for constraint attributes in app namespace
            if (uri == AUTO_URI || uri == SdkConstants.SHERPA_URI) {
                when {
                    HORIZONTAL_CONSTRAINT_ATTRS.contains(localName) -> hasHorizontalConstraint = true
                    VERTICAL_CONSTRAINT_ATTRS.contains(localName) -> hasVerticalConstraint = true
                }
            }
        }

        // If the view has no constraints at all, report it
        if (!hasHorizontalConstraint || !hasVerticalConstraint) {
            // Only report if there are no constraints (not just missing one direction)
            // Actually spec says both horizontal AND vertical must be present
            val missing = mutableListOf<String>()
            if (!hasHorizontalConstraint) missing.add("horizontal")
            if (!hasVerticalConstraint) missing.add("vertical")

            if (missing.isNotEmpty()) {
                val message = if (missing.size == 2) {
                    "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints"
                } else {
                    "This view is missing ${missing[0]} constraints"
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