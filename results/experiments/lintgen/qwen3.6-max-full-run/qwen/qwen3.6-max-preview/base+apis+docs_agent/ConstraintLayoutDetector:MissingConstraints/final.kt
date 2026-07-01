package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ConstraintLayoutDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(
        "android.support.constraint.ConstraintLayout",
        "androidx.constraintlayout.widget.ConstraintLayout"
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                checkChild(context, node as Element)
            }
        }
    }

    private fun checkChild(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName.contains("Guideline") || tagName.contains("Placeholder") || tagName.contains("MockView")) {
            return
        }

        var hasHorizontal = false
        var hasVertical = false

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.localName ?: attr.name
            if (name.startsWith("layout_constraint")) {
                when {
                    name.contains("Left") || name.contains("Right") ||
                    name.contains("Start") || name.contains("End") -> hasHorizontal = true
                    name.contains("Top") || name.contains("Bottom") ||
                    name.contains("Baseline") -> hasVertical = true
                    name.contains("Circle") -> {
                        hasHorizontal = true
                        hasVertical = true
                    }
                }
            }
        }

        if (!hasHorizontal || !hasVertical) {
            val missing = buildString {
                if (!hasHorizontal) append("horizontal")
                if (!hasHorizontal && !hasVertical) append(" and ")
                if (!hasVertical) append("vertical")
            }
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This view is not constrained. It only has design-time positions, so it will jump to (0,0) at runtime unless you add $missing constraints"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "MissingConstraints",
            "Missing Constraints in ConstraintLayout",
            "The layout editor allows you to place widgets anywhere on the canvas, " +
            "and it records the current position with designtime attributes (such as " +
            "`layout_editor_absoluteX`). These attributes are **not** applied at " +
            "runtime, so if you push your layout on a device, the widgets may appear " +
            "in a different location than shown in the editor. To fix this, make sure " +
            "a widget has both horizontal and vertical constraints by dragging from " +
            "the edge connections.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(ConstraintLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}