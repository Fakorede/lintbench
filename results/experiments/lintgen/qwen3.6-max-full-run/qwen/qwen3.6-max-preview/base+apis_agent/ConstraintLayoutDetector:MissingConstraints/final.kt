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
    companion object {
        val ISSUE = Issue.create(
            "MissingConstraints",
            "Missing Constraints in ConstraintLayout",
            "The layout editor allows you to place widgets anywhere on the canvas, " +
            "and it records the current position with designtime attributes (such as " +
            "`layout_editor_absoluteX`). These attributes are **not** applied at " +
            "runtime, so if you push your layout on a device, the widgets may appear " +
            "in a different location than shown in the editor. To fix this, make sure " +
            "a widget has both horizontal and vertical constraints by dragging from " +
            "the edge connections.",
            Category.CORRECTNESS, 6, Severity.ERROR,
            Implementation(ConstraintLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )

        private val CONSTRAINT_LAYOUTS = listOf(
            "androidx.constraintlayout.widget.ConstraintLayout",
            "android.support.constraint.ConstraintLayout",
            "ConstraintLayout"
        )

        private val IGNORED_VIEWS = setOf(
            "Guideline", "Group", "Placeholder", "Layer", "Flow", "MockView", "Barrier",
            "ImageFilterView", "ImageFilterButton"
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? = CONSTRAINT_LAYOUTS

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                checkChild(context, node as Element)
            }
        }
    }

    private fun checkChild(context: XmlContext, child: Element) {
        val tag = child.tagName
        val simpleName = tag.substringAfterLast('.')
        if (simpleName in IGNORED_VIEWS) return
        if (simpleName == "merge" || simpleName == "include" || simpleName == "fragment") return

        var hasHorizontal = false
        var hasVertical = false

        val width = child.getAttribute("android:layout_width")
        val height = child.getAttribute("android:layout_height")

        if (width == "match_parent" || width == "fill_parent") {
            hasHorizontal = true
        }
        if (height == "match_parent" || height == "fill_parent") {
            hasVertical = true
        }

        val attributes = child.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.localName ?: attr.name
            if (!name.startsWith("layout_constraint")) continue

            if (name.contains("Left") || name.contains("Right") ||
                name.contains("Start") || name.contains("End") ||
                name.contains("Circle")
            ) {
                hasHorizontal = true
            }
            if (name.contains("Top") || name.contains("Bottom") ||
                name.contains("Baseline") || name.contains("Circle")
            ) {
                hasVertical = true
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
                context.getLocation(child),
                "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the missing $missing constraints"
            )
        }
    }
}