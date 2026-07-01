package com.android.tools.lint.checks

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
        private val IMPLEMENTATION = Implementation(
            ConstraintLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, \
                and it records the current position with designtime attributes (such as \
                `layout_editor_absoluteX`). These attributes are not applied at \
                runtime, so if you push your layout on a device, the widgets may appear \
                in a different location than shown in the editor. To fix this, make sure \
                a widget has both horizontal and vertical constraints by dragging from \
                the edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )

        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf"
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf"
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
            val node = childNodes.item(i)
            if (node is Element) {
                val tagName = node.tagName
                if (tagName == "Guideline" ||
                    tagName == "Barrier" ||
                    tagName == "Group" ||
                    tagName.endsWith(".Guideline") ||
                    tagName.endsWith(".Barrier") ||
                    tagName.endsWith(".Group") ||
                    tagName.endsWith(".Placeholder")
                ) {
                    continue
                }

                var hasHorizontal = false
                var hasVertical = false
                var hasCircle = false

                val attributes = node.attributes
                for (j in 0 until attributes.length) {
                    val attr = attributes.item(j)
                    val localName = attr.localName ?: attr.nodeName.substringAfter(':')
                    val isAuto = attr.namespaceURI == "http://schemas.android.com/apk/res-auto" ||
                            attr.nodeName.startsWith("app:") ||
                            attr.nodeName.startsWith("local:")

                    if (isAuto) {
                        if (HORIZONTAL_CONSTRAINTS.contains(localName)) {
                            hasHorizontal = true
                        } else if (VERTICAL_CONSTRAINTS.contains(localName)) {
                            hasVertical = true
                        } else if (localName == "layout_constraintCircle") {
                            hasCircle = true
                        }
                    }
                }

                if (hasCircle) {
                    hasHorizontal = true
                    hasVertical = true
                }

                val message = when {
                    !hasHorizontal && !hasVertical ->
                        "This view is not constrained. It only has designtime constraints, so it will jump to (0,0) at runtime unless you add the constraints"
                    !hasHorizontal ->
                        "This view is not constrained horizontally: at runtime it will jump to the left unless you add a constraint"
                    !hasVertical ->
                        "This view is not constrained vertically: at runtime it will jump to the top unless you add a constraint"
                    else -> null
                }

                if (message != null) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                }
            }
        }
    }
}