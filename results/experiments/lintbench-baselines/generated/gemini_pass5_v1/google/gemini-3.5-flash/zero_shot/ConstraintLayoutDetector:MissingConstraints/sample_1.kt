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

    override fun getApplicableElements(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName
        if (parentTag != "androidx.constraintlayout.widget.ConstraintLayout" &&
            parentTag != "android.support.constraint.ConstraintLayout" &&
            parentTag != "ConstraintLayout"
        ) {
            return
        }

        val tag = element.tagName
        if (tag.endsWith("Guideline") || tag.endsWith("Barrier") || tag.endsWith("Group") || tag.endsWith("Placeholder")) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.localName ?: continue
            val namespace = attr.namespaceURI
            if (namespace == "http://schemas.android.com/apk/res-auto") {
                if (name.startsWith("layout_constraintLeft_") ||
                    name.startsWith("layout_constraintRight_") ||
                    name.startsWith("layout_constraintStart_") ||
                    name.startsWith("layout_constraintEnd_")
                ) {
                    hasHorizontal = true
                } else if (name.startsWith("layout_constraintTop_") ||
                    name.startsWith("layout_constraintBottom_") ||
                    name == "layout_baselineToBaselineOf"
                ) {
                    hasVertical = true
                }
            }
        }

        if (!hasHorizontal || !hasVertical) {
            val message = when {
                !hasHorizontal && !hasVertical ->
                    "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints"
                !hasHorizontal ->
                    "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint"
                else ->
                    "This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint"
            }
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                message
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are **not** applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
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