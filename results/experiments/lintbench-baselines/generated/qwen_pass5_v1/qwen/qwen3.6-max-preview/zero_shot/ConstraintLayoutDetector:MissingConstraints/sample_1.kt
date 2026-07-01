package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class ConstraintLayoutDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? = listOf(
        "android.support.constraint.ConstraintLayout",
        "androidx.constraintlayout.widget.ConstraintLayout"
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkChild(context, child as Element)
            }
        }
    }

    private fun checkChild(context: XmlContext, child: Element) {
        val tagName = child.tagName
        if (isHelperView(tagName)) return

        val hasHorizontal = hasHorizontalConstraints(child)
        val hasVertical = hasVerticalConstraints(child)

        val width = child.getAttribute("android:layout_width")
        val height = child.getAttribute("android:layout_height")
        val isWidthMatchParent = width == "match_parent" || width == "fill_parent"
        val isHeightMatchParent = height == "match_parent" || height == "fill_parent"

        val constrainedHorizontally = hasHorizontal || isWidthMatchParent
        val constrainedVertically = hasVertical || isHeightMatchParent

        if (!constrainedHorizontally || !constrainedVertically) {
            val missing = buildString {
                if (!constrainedHorizontally) append("horizontal")
                if (!constrainedHorizontally && !constrainedVertically) append(" and ")
                if (!constrainedVertically) append("vertical")
            }
            context.report(
                ISSUE_MISSING_CONSTRAINTS,
                child,
                context.getLocation(child),
                "This view is not constrained. It only has design-time positions, so it will jump to (0,0) at runtime unless you add the $missing constraints"
            )
        }
    }

    private fun isHelperView(tagName: String): Boolean {
        val simpleName = if (tagName.contains('.')) tagName.substringAfterLast('.') else tagName
        return simpleName in listOf(
            "Guideline", "Barrier", "Group", "Placeholder",
            "Flow", "Layer", "CircularFlow", "MotionHelper",
            "MotionInterpolator", "ViewOverlay", "ReactView"
        )
    }

    private fun hasHorizontalConstraints(element: Element): Boolean {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val name = attrs.item(i).nodeName
            val localName = if (':' in name) name.substringAfter(':') else name
            if (localName.startsWith("layout_constraintLeft_") ||
                localName.startsWith("layout_constraintRight_") ||
                localName.startsWith("layout_constraintStart_") ||
                localName.startsWith("layout_constraintEnd_") ||
                localName == "layout_constraintCircle") {
                return true
            }
        }
        return false
    }

    private fun hasVerticalConstraints(element: Element): Boolean {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val name = attrs.item(i).nodeName
            val localName = if (':' in name) name.substringAfter(':') else name
            if (localName.startsWith("layout_constraintTop_") ||
                localName.startsWith("layout_constraintBottom_") ||
                localName.startsWith("layout_constraintBaseline_") ||
                localName == "layout_constraintCircle") {
                return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE_MISSING_CONSTRAINTS: Issue = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing constraints in ConstraintLayout",
            explanation = """
                The layout editor allows you to place widgets anywhere on the canvas, \
                and it records the current position with designtime attributes (such as \
                `layout_editor_absoluteX`). These attributes are **not** applied at \
                runtime, so if you push your layout on a device, the widgets may appear \
                in a different location than shown in the editor. To fix this, make sure \
                a widget has both horizontal and vertical constraints by dragging from \
                the edge connections.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )
    }
}