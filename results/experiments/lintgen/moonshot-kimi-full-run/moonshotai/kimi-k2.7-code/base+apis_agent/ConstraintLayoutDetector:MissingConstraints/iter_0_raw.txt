package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): Collection<String> = CONSTRAINT_LAYOUT_TAGS

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue

            val childElement = child as Element
            val tagName = childElement.tagName
            if (tagName == "requestFocus" || tagName == "tag") continue
            if (HELPER_SUFFIXES.any { tagName.endsWith(it) }) continue

            val hasHorizontal = hasConstraint(childElement, HORIZONTAL_CONSTRAINTS)
            val hasVertical = hasConstraint(childElement, VERTICAL_CONSTRAINTS)

            if (!hasHorizontal || !hasVertical) {
                val message = buildString {
                    append("This view is missing ")
                    when {
                        !hasHorizontal && !hasVertical -> append("both horizontal and vertical constraints")
                        !hasHorizontal -> append("a horizontal constraint")
                        else -> append("a vertical constraint")
                    }
                    append("; it may not be positioned correctly at runtime.")
                }
                context.report(ISSUE, context.getLocation(childElement), message)
            }
        }
    }

    private fun hasConstraint(element: Element, attrs: Set<String>): Boolean {
        for (attr in attrs) {
            val value = element.getAttributeNS(AUTO_URI, attr)
            if (value.isNotBlank() && value != "@null") {
                return true
            }
        }
        return false
    }

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        private val CONSTRAINT_LAYOUT_TAGS = listOf(
            "androidx.constraintlayout.widget.ConstraintLayout",
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "android.support.constraint.ConstraintLayout",
            "ConstraintLayout"
        )

        private val HORIZONTAL_CONSTRAINTS = setOf(
            "layout_constraintStart_toStartOf",
            "layout_constraintStart_toEndOf",
            "layout_constraintEnd_toStartOf",
            "layout_constraintEnd_toEndOf",
            "layout_constraintLeft_toLeftOf",
            "layout_constraintLeft_toRightOf",
            "layout_constraintRight_toLeftOf",
            "layout_constraintRight_toRightOf",
            "layout_constraintCircle"
        )

        private val VERTICAL_CONSTRAINTS = setOf(
            "layout_constraintTop_toTopOf",
            "layout_constraintTop_toBottomOf",
            "layout_constraintBottom_toTopOf",
            "layout_constraintBottom_toBottomOf",
            "layout_constraintBaseline_toBaselineOf",
            "layout_constraintCircle"
        )

        private val HELPER_SUFFIXES = setOf(
            "Guideline",
            "Barrier",
            "Group",
            "Placeholder",
            "Layer",
            "Helper",
            "Flow",
            "Carousel",
            "MotionEffect",
            "Constraints"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, and it records
                the current position with designtime attributes (such as layout_editor_absoluteX).
                These attributes are not applied at runtime, so if you push your layout on a device,
                the widgets may appear in a different location than shown in the editor. To fix this,
                make sure a widget has both horizontal and vertical constraints by dragging from the
                edge connections.
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