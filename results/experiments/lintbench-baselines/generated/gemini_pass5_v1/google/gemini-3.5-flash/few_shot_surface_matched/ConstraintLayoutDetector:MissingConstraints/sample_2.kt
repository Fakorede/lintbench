package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element ?: return
        val parentTag = parent.tagName
        if (!parentTag.endsWith("ConstraintLayout")) {
            return
        }

        val tagName = element.tagName
        if (tagName.endsWith("Guideline") ||
            tagName.endsWith("Barrier") ||
            tagName.endsWith("Group") ||
            tagName.endsWith("Placeholder") ||
            tagName.endsWith("ConstraintHelper") ||
            tagName == "include" ||
            tagName == "merge") {
            return
        }

        var hasHorizontalConstraint = false
        var hasVerticalConstraint = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
            val namespace = attr.namespaceURI
            if (namespace == AUTO_URI) {
                val localName = attr.localName ?: attr.name.substringAfter(':')
                when (localName) {
                    "layout_constraintLeft_toLeftOf",
                    "layout_constraintLeft_toRightOf",
                    "layout_constraintRight_toLeftOf",
                    "layout_constraintRight_toRightOf",
                    "layout_constraintStart_toStartOf",
                    "layout_constraintStart_toEndOf",
                    "layout_constraintEnd_toStartOf",
                    "layout_constraintEnd_toEndOf" -> {
                        hasHorizontalConstraint = true
                    }
                    "layout_constraintTop_toTopOf",
                    "layout_constraintTop_toBottomOf",
                    "layout_constraintBottom_toTopOf",
                    "layout_constraintBottom_toBottomOf",
                    "layout_constraintBaseline_toBaselineOf" -> {
                        hasVerticalConstraint = true
                    }
                    "layout_constraintCircle" -> {
                        hasHorizontalConstraint = true
                        hasVerticalConstraint = true
                    }
                }
            }
        }

        val hasMissingHorizontal = !hasHorizontalConstraint
        val hasMissingVertical = !hasVerticalConstraint

        if (hasMissingHorizontal || hasMissingVertical) {
            val message = when {
                hasMissingHorizontal && hasMissingVertical ->
                    "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints"
                hasMissingHorizontal ->
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
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are NOT applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILE_SCOPE
            )
        )
    }
}