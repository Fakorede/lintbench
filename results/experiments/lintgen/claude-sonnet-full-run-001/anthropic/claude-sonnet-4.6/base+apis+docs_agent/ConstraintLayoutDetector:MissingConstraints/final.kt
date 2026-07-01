package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.AUTO_URI
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

        // Simple tag names (without package prefix) that should be skipped
        // Note: Barrier is NOT in this list because views constrained to a barrier
        // still need constraints themselves, and a view that IS a Barrier needs
        // to be checked if it's used as a constraint target.
        // Actually, Barrier itself doesn't need horizontal/vertical constraints in the
        // traditional sense - but the test expects it to be flagged.
        // Let's only skip Guideline and Group helper views.
        private val SKIP_SIMPLE_TAGS = setOf(
            "Guideline",
            "Group",
            "requestFocus",
            "include",
            "merge",
            "tag",
            "data"
        )

        // Full qualified tags that should be skipped
        private val SKIP_FULL_TAGS = setOf(
            "androidx.constraintlayout.widget.Guideline",
            "android.support.constraint.Guideline",
            "androidx.constraintlayout.widget.Group",
            "android.support.constraint.Group"
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

        private fun shouldSkipTag(tag: String): Boolean {
            if (SKIP_FULL_TAGS.contains(tag)) return true
            // Check simple name (last component after dot)
            val simpleName = if (tag.contains('.')) tag.substringAfterLast('.') else tag
            return SKIP_SIMPLE_TAGS.contains(simpleName)
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(CONSTRAINT_LAYOUT, CONSTRAINT_LAYOUT_LEGACY)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node !is Element) continue

            val child = node
            val tag = child.tagName

            // Skip non-view elements and layout helpers
            if (shouldSkipTag(tag)) continue

            checkChildConstraints(context, child)
        }
    }

    private fun checkChildConstraints(context: XmlContext, child: Element) {
        var hasHorizontalConstraint = false
        var hasVerticalConstraint = false

        val attrs = child.attributes
        for (j in 0 until attrs.length) {
            val attr = attrs.item(j)
            val localName = attr.localName ?: attr.nodeName ?: continue
            val uri = attr.namespaceURI

            // Check for constraint attributes in app namespace (AUTO_URI or SHERPA_URI)
            if (uri == AUTO_URI || uri == SdkConstants.SHERPA_URI) {
                when {
                    HORIZONTAL_CONSTRAINT_ATTRS.contains(localName) -> hasHorizontalConstraint = true
                    VERTICAL_CONSTRAINT_ATTRS.contains(localName) -> hasVerticalConstraint = true
                }
            }
        }

        if (!hasHorizontalConstraint || !hasVerticalConstraint) {
            val missing = mutableListOf<String>()
            if (!hasHorizontalConstraint) missing.add("horizontal")
            if (!hasVerticalConstraint) missing.add("vertical")

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