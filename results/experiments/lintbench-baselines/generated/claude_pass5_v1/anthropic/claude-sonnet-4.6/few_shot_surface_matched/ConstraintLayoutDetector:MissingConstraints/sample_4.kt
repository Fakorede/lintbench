package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.FQCN_CONSTRAINT_LAYOUT
import com.android.SdkConstants.TAG_INCLUDE
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

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            FQCN_CONSTRAINT_LAYOUT,
            FQCN_CONSTRAINT_LAYOUT.replace(".", "/"),
            "android.support.constraint.ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node !is Element) continue
            val child = node

            val tag = child.tagName
            // Skip tags that don't need constraints
            if (tag == TAG_INCLUDE ||
                tag == "requestFocus" ||
                tag == "fragment" ||
                tag.startsWith("<") ) {
                continue
            }

            // Skip guidelines and barriers - they manage their own positioning
            if (tag.endsWith("Guideline") || tag.endsWith("Barrier") || tag.endsWith("Group") || tag.endsWith("Placeholder")) {
                continue
            }

            // Check if the child has layout_width and layout_height (i.e., it's a real view)
            val hasLayoutWidth = child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
            val hasLayoutHeight = child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
            if (!hasLayoutWidth && !hasLayoutHeight) {
                continue
            }

            val hasHorizontal = hasHorizontalConstraint(child)
            val hasVertical = hasVerticalConstraint(child)

            if (!hasHorizontal || !hasVertical) {
                val id = child.getAttributeNS(ANDROID_URI, ATTR_ID)
                val idStr = if (id.isNotEmpty()) "`${id.removePrefix("@+id/").removePrefix("@id/")}` " else ""
                val missing = when {
                    !hasHorizontal && !hasVertical -> "both a horizontal and vertical"
                    !hasHorizontal -> "a horizontal"
                    else -> "a vertical"
                }
                context.report(
                    ISSUE,
                    child,
                    context.getNameLocation(child),
                    "This view ${idStr}is not constrained. It only has designtime positions, " +
                        "so it will jump to (0,0) at runtime unless you add the constraints. " +
                        "Also missing $missing constraint."
                )
            }
        }
    }

    private fun hasHorizontalConstraint(element: Element): Boolean {
        // Check for horizontal constraints in both app: (AUTO_URI) and no-namespace attributes
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val name = attr.localName ?: attr.nodeName ?: continue
            val ns = attr.namespaceURI

            if (ns == AUTO_URI || ns == null || ns.isEmpty()) {
                if (isHorizontalConstraintAttr(name)) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasVerticalConstraint(element: Element): Boolean {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val name = attr.localName ?: attr.nodeName ?: continue
            val ns = attr.namespaceURI

            if (ns == AUTO_URI || ns == null || ns.isEmpty()) {
                if (isVerticalConstraintAttr(name)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isHorizontalConstraintAttr(name: String): Boolean {
        return name == "layout_constraintLeft_toLeftOf" ||
            name == "layout_constraintLeft_toRightOf" ||
            name == "layout_constraintRight_toLeftOf" ||
            name == "layout_constraintRight_toRightOf" ||
            name == "layout_constraintStart_toStartOf" ||
            name == "layout_constraintStart_toEndOf" ||
            name == "layout_constraintEnd_toStartOf" ||
            name == "layout_constraintEnd_toEndOf" ||
            name == "layout_constraintHorizontal_chainStyle" ||
            name == "layout_constraintCircle"
    }

    private fun isVerticalConstraintAttr(name: String): Boolean {
        return name == "layout_constraintTop_toTopOf" ||
            name == "layout_constraintTop_toBottomOf" ||
            name == "layout_constraintBottom_toTopOf" ||
            name == "layout_constraintBottom_toBottomOf" ||
            name == "layout_constraintBaseline_toBaselineOf" ||
            name == "layout_constraintBaseline_toTopOf" ||
            name == "layout_constraintBaseline_toBottomOf" ||
            name == "layout_constraintVertical_chainStyle" ||
            name == "layout_constraintCircle"
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation =
                """
                The layout editor allows you to place widgets anywhere on the canvas, \
                and it records the current position with designtime attributes (such as \
                `layout_editor_absoluteX`). These attributes are **not** applied at \
                runtime, so if you push your layout on a device, the widgets may appear \
                in a different location than shown in the editor. To fix this, make sure \
                a widget has both horizontal and vertical constraints by dragging from \
                the edge connections.
                """,
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