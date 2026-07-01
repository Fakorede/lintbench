package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT
import com.android.SdkConstants.TOOLS_URI
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
        return listOf(CLASS_CONSTRAINT_LAYOUT.newName(), CLASS_CONSTRAINT_LAYOUT.oldName())
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            checkChild(context, child)
        }
    }

    private fun checkChild(context: XmlContext, child: Element) {
        val tagName = child.tagName

        // Skip special tags that don't need constraints
        if (tagName == "include" ||
            tagName == "merge" ||
            tagName == "requestFocus" ||
            tagName == "Guideline" ||
            tagName.endsWith(".Guideline") ||
            tagName == "Group" ||
            tagName.endsWith(".Group") ||
            tagName == "Barrier" ||
            tagName.endsWith(".Barrier") ||
            tagName == "MockView"
        ) {
            return
        }

        // Check if the child has a 0dp width/height that implies match_constraint
        val hasHorizontalConstraint = hasHorizontalConstraint(child)
        val hasVerticalConstraint = hasVerticalConstraint(child)

        if (!hasHorizontalConstraint || !hasVerticalConstraint) {
            // Only report if the widget has a position defined by editor absolute attributes
            // (meaning it was placed in editor but not properly constrained), or always report
            // missing constraints regardless.
            val idAttr = child.getAttributeNS(ANDROID_URI, ATTR_ID)
            val location = context.getElementLocation(child)

            val missing = buildString {
                if (!hasHorizontalConstraint && !hasVerticalConstraint) {
                    append("horizontal and vertical")
                } else if (!hasHorizontalConstraint) {
                    append("horizontal")
                } else {
                    append("vertical")
                }
            }

            val idDesc = if (idAttr.isNotEmpty()) {
                "`${idAttr.removePrefix("@+id/").removePrefix("@id/")}` "
            } else {
                ""
            }

            context.report(
                ISSUE,
                child,
                location,
                "This view is not constrained. It only has designtime positions, so it will " +
                    "jump to (0,0) at runtime unless you add the constraints: " +
                    "Widget ${idDesc}is missing $missing constraints"
            )
        }
    }

    private fun hasHorizontalConstraint(element: Element): Boolean {
        // Check for any of the horizontal constraint attributes
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val name = attr.localName ?: continue
            val ns = attr.namespaceURI ?: continue

            if (ns != AUTO_URI && ns != ANDROID_URI) continue

            if (name == "layout_toLeftOf" ||
                name == "layout_toRightOf" ||
                name == "layout_toStartOf" ||
                name == "layout_toEndOf" ||
                name == "layout_constraintLeft_toLeftOf" ||
                name == "layout_constraintLeft_toRightOf" ||
                name == "layout_constraintRight_toLeftOf" ||
                name == "layout_constraintRight_toRightOf" ||
                name == "layout_constraintStart_toStartOf" ||
                name == "layout_constraintStart_toEndOf" ||
                name == "layout_constraintEnd_toStartOf" ||
                name == "layout_constraintEnd_toEndOf" ||
                name == "layout_constraintHorizontal_bias" ||
                name == "layout_centerHorizontal" ||
                name == "layout_centerInParent"
            ) {
                return true
            }
        }

        // Check for layout_width = match_parent which implies horizontal constraint
        val layoutWidth = element.getAttributeNS(ANDROID_URI, "layout_width")
        if (layoutWidth == "match_parent") {
            return true
        }

        return false
    }

    private fun hasVerticalConstraint(element: Element): Boolean {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val name = attr.localName ?: continue
            val ns = attr.namespaceURI ?: continue

            if (ns != AUTO_URI && ns != ANDROID_URI) continue

            if (name == "layout_above" ||
                name == "layout_below" ||
                name == "layout_alignTop" ||
                name == "layout_alignBottom" ||
                name == "layout_constraintTop_toTopOf" ||
                name == "layout_constraintTop_toBottomOf" ||
                name == "layout_constraintBottom_toTopOf" ||
                name == "layout_constraintBottom_toBottomOf" ||
                name == "layout_constraintBaseline_toBaselineOf" ||
                name == "layout_constraintVertical_bias" ||
                name == "layout_centerVertical" ||
                name == "layout_centerInParent"
            ) {
                return true
            }
        }

        // Check for layout_height = match_parent which implies vertical constraint
        val layoutHeight = element.getAttributeNS(ANDROID_URI, "layout_height")
        if (layoutHeight == "match_parent") {
            return true
        }

        return false
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MissingConstraints",
            briefDescription = "Missing Constraints in ConstraintLayout",
            explanation =
                """
                The layout editor allows you to place widgets anywhere on the canvas, and it \
                records the current position with designtime attributes (such as \
                `layout_editor_absoluteX`). These attributes are **not** applied at runtime, \
                so if you push your layout on a device, the widgets may appear in a different \
                location than shown in the editor. To fix this, make sure a widget has both \
                horizontal and vertical constraints by dragging from the edge connections.
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