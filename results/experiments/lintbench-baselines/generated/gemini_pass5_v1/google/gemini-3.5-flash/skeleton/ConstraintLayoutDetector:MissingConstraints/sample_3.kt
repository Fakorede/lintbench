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
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintStart_toStartOf",
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

        private val IMPLEMENTATION = Implementation(
            ConstraintLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX`). These attributes are NOT applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor.
                
                To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
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
            val childNode = childNodes.item(i)
            if (childNode is Element) {
                val child = childNode
                val tagName = child.tagName

                if (tagName.endsWith("Guideline") ||
                    tagName.endsWith("Barrier") ||
                    tagName.endsWith("Group") ||
                    tagName.endsWith("Placeholder") ||
                    tagName.endsWith("Flow")
                ) {
                    continue
                }

                if (!child.hasAttributeNS(ANDROID_URI, "layout_width") &&
                    !child.hasAttributeNS(ANDROID_URI, "layout_height")
                ) {
                    continue
                }

                var hasHorizontal = false
                for (attr in HORIZONTAL_CONSTRAINTS) {
                    if (child.hasAttributeNS(AUTO_URI, attr)) {
                        hasHorizontal = true
                        break
                    }
                }

                var hasVertical = false
                for (attr in VERTICAL_CONSTRAINTS) {
                    if (child.hasAttributeNS(AUTO_URI, attr)) {
                        hasVertical = true
                        break
                    }
                }

                if (child.hasAttributeNS(AUTO_URI, "layout_constraintCircle")) {
                    hasHorizontal = true
                    hasVertical = true
                }

                if (!hasHorizontal || !hasVertical) {
                    val missing = when {
                        !hasHorizontal && !hasVertical -> "horizontal and vertical"
                        !hasHorizontal -> "horizontal"
                        else -> "vertical"
                    }
                    val message = "This view is not constrained $missing. At runtime it will jump to the left/top unless you add a constraint."
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
}