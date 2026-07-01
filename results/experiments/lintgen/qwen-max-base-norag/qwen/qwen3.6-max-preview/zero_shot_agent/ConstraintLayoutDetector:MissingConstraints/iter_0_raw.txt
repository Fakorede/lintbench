package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class ConstraintLayoutDetector : LayoutDetector() {
    companion object {
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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ConstraintLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val HELPER_VIEWS = setOf(
            "Guideline", "Barrier", "Group", "Placeholder", "Layer", "Flow", "MockView"
        )
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: XmlElement) {
        if (!element.localName.endsWith("ConstraintLayout")) return

        for (child in element.children()) {
            if (child !is XmlElement) continue
            val tagName = child.localName
            if (isHelperView(tagName)) continue

            val hasH = hasHorizontalConstraint(child)
            val hasV = hasVerticalConstraint(child)

            if (!hasH || !hasV) {
                val missing = mutableListOf<String>()
                if (!hasH) missing.add("horizontal")
                if (!hasV) missing.add("vertical")
                val message = "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints. Missing ${missing.joinToString(" and ")} constraints."
                context.report(ISSUE, context.getLocation(child), message)
            }
        }
    }

    private fun isHelperView(tagName: String): Boolean {
        return HELPER_VIEWS.any { tagName == it || tagName.endsWith(".$it") }
    }

    private fun hasHorizontalConstraint(element: XmlElement): Boolean {
        return element.attributes().any { attr ->
            val name = attr.localName
            name.startsWith("layout_constraint") &&
                    (name.contains("Left") || name.contains("Right") ||
                     name.contains("Start") || name.contains("End") ||
                     name.contains("Circle"))
        }
    }

    private fun hasVerticalConstraint(element: XmlElement): Boolean {
        return element.attributes().any { attr ->
            val name = attr.localName
            name.startsWith("layout_constraint") &&
                    (name.contains("Top") || name.contains("Bottom") ||
                     name.contains("Baseline") || name.contains("Circle"))
        }
    }
}