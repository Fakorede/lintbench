package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CLASS_CONSTRAINT_LAYOUT
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
        return listOf(CLASS_CONSTRAINT_LAYOUT.newName(), CLASS_CONSTRAINT_LAYOUT.oldName())
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            checkChild(context, child)
        }
    }

    private fun checkChild(context: XmlContext, element: Element) {
        val tag = element.tagName ?: return

        // Skip tags that don't need constraints
        if (tag == TAG_INCLUDE ||
            tag == "requestFocus" ||
            tag == "tag" ||
            tag == "Guideline" ||
            tag.endsWith(".Guideline") ||
            tag == "androidx.constraintlayout.widget.Guideline" ||
            tag == "android.support.constraint.Guideline") {
            return
        }

        // Check for horizontal constraint
        val hasHorizontalConstraint = hasHorizontalConstraint(element)
        val hasVerticalConstraint = hasVerticalConstraint(element)

        if (!hasHorizontalConstraint || !hasVerticalConstraint) {
            val id = element.getAttributeNS(ANDROID_URI, ATTR_ID)
            val label = if (id.isNotEmpty()) {
                "`${id.removePrefix("@+id/").removePrefix("@id/")}` (${element.tagName})"
            } else {
                "`${element.tagName}`"
            }

            val missing = when {
                !hasHorizontalConstraint && !hasVerticalConstraint ->
                    "horizontal and vertical"
                !hasHorizontalConstraint -> "horizontal"
                else -> "vertical"
            }

            val locationNode = if (element.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
                element.getAttributeNodeNS(ANDROID_URI, ATTR_ID)
            } else {
                null
            }

            val location = if (locationNode != null) {
                context.getValueLocation(locationNode)
            } else {
                context.getElementLocation(element)
            }

            context.report(
                ISSUE,
                element,
                location,
                "This view is not constrained. It only has designtime positions, so it will " +
                    "jump to (0,0) at runtime unless you add the constraints: $label is missing " +
                    "$missing constraints"
            )
        }
    }

    private fun hasHorizontalConstraint(element: Element): Boolean {
        // Check layout_width for match_parent or match_constraint equivalent
        val width = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
        if (width == "match_parent") return true

        // Check all constraint attributes for horizontal constraints
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val name = attr.localName ?: continue
            val ns = attr.namespaceURI ?: continue
            if (ns != AUTO_URI && ns != "http://schemas.android.com/apk/res-auto") continue

            if (name == "layout_constraintLeft_toLeftOf" ||
                name == "layout_constraintLeft_toRightOf" ||
                name == "layout_constraintRight_toLeftOf" ||
                name == "layout_constraintRight_toRightOf" ||
                name == "layout_constraintStart_toStartOf" ||
                name == "layout_constraintStart_toEndOf" ||
                name == "layout_constraintEnd_toStartOf" ||
                name == "layout_constraintEnd_toEndOf" ||
                name == "layout_constraintHorizontal_bias") {
                return true
            }
        }
        return false
    }

    private fun hasVerticalConstraint(element: Element): Boolean {
        // Check layout_height for match_parent
        val height = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
        if (height == "match_parent") return true

        // Check all constraint attributes for vertical constraints
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val name = attr.localName ?: continue
            val ns = attr.namespaceURI ?: continue
            if (ns != AUTO_URI && ns != "http://schemas.android.com/apk/res-auto") continue

            if (name == "layout_constraintTop_toTopOf" ||
                name == "layout_constraintTop_toBottomOf" ||
                name == "layout_constraintBottom_toTopOf" ||
                name == "layout_constraintBottom_toBottomOf" ||
                name == "layout_constraintBaseline_toBaselineOf" ||
                name == "layout_constraintVertical_bias") {
                return true
            }
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