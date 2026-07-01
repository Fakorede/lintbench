package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

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
                The layout editor can place widgets anywhere on the canvas and record \
                their positions with design-time attributes such as \
                `layout_editor_absoluteX`. Those attributes are not applied at runtime, \
                so widgets without horizontal and vertical constraints may appear in a \
                different location on a device. Make sure each widget inside a \
                ConstraintLayout has both horizontal and vertical constraints.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val parent = element.parentNode as? org.w3c.dom.Element ?: return
        if (!isConstraintLayout(parent) || isConstraintHelper(element)) {
            return
        }

        val hasHorizontal = hasHorizontalConstraint(element)
        val hasVertical = hasVerticalConstraint(element)

        if (!hasHorizontal) {
            reportMissing(context, element, "horizontally")
        }
        if (!hasVertical) {
            reportMissing(context, element, "vertically")
        }
    }

    private fun isConstraintLayout(element: org.w3c.dom.Element): Boolean {
        val tag = element.tagName
        return tag == "androidx.constraintlayout.widget.ConstraintLayout" ||
                tag == "android.support.constraint.ConstraintLayout" ||
                tag == "ConstraintLayout" ||
                tag.endsWith(".ConstraintLayout")
    }

    private fun isConstraintHelper(element: org.w3c.dom.Element): Boolean {
        val tag = element.tagName
        return tag == "androidx.constraintlayout.widget.Guideline" ||
                tag == "android.support.constraint.Guideline" ||
                tag == "Guideline" ||
                tag == "androidx.constraintlayout.widget.Barrier" ||
                tag == "android.support.constraint.Barrier" ||
                tag == "Barrier"
    }

    private fun hasHorizontalConstraint(element: org.w3c.dom.Element): Boolean {
        return hasAnyAttribute(element, HORIZONTAL_CONSTRAINTS)
    }

    private fun hasVerticalConstraint(element: org.w3c.dom.Element): Boolean {
        return hasAnyAttribute(element, VERTICAL_CONSTRAINTS)
    }

    private fun hasAnyAttribute(element: org.w3c.dom.Element, names: Set<String>): Boolean {
        val attrs = element.attributes ?: return false
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) ?: continue
            if (attr.localName in names) {
                return true
            }
        }
        return false
    }

    private fun reportMissing(
        context: XmlContext,
        element: org.w3c.dom.Element,
        direction: String,
    ) {
        val absoluteAttr = if (direction == "horizontally") {
            "layout_editor_absoluteX"
        } else {
            "layout_editor_absoluteY"
        }
        val attr = findAttributeByLocalName(element, absoluteAttr)
        val location = attr?.let { context.getLocation(it) } ?: context.getLocation(element)
        val edge = if (direction == "horizontally") "left" else "top"
        val message = "This view is not constrained $direction. " +
                "At runtime it will jump to the $edge."
        context.report(ISSUE, location, message)
    }

    private fun findAttributeByLocalName(
        element: org.w3c.dom.Element,
        localName: String,
    ): org.w3c.dom.Attr? {
        val attrs = element.attributes ?: return null
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as? org.w3c.dom.Attr ?: continue
            if (attr.localName == localName) {
                return attr
            }
        }
        return null
    }

    private val HORIZONTAL_CONSTRAINTS = setOf(
        "layout_constraintLeft_toLeftOf",
        "layout_constraintLeft_toRightOf",
        "layout_constraintRight_toLeftOf",
        "layout_constraintRight_toRightOf",
        "layout_constraintStart_toStartOf",
        "layout_constraintStart_toEndOf",
        "layout_constraintEnd_toStartOf",
        "layout_constraintEnd_toEndOf",
        "layout_constraintCircle",
    )

    private val VERTICAL_CONSTRAINTS = setOf(
        "layout_constraintTop_toTopOf",
        "layout_constraintTop_toBottomOf",
        "layout_constraintBottom_toTopOf",
        "layout_constraintBottom_toBottomOf",
        "layout_constraintBaseline_toBaselineOf",
        "layout_constraintCircle",
    )
}