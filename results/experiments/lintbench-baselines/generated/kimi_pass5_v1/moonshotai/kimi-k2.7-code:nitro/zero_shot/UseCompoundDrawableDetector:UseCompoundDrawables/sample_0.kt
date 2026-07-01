package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class UseCompoundDrawableDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = element.childNodes.let { nodes ->
            (0 until nodes.length).map(nodes::item).filterIsInstance<Element>()
        }

        if (children.size != 2) return

        val imageView = children.find { it.tagName == SdkConstants.TAG_IMAGE_VIEW } ?: return
        val textView = children.find { it.tagName == SdkConstants.TAG_TEXT_VIEW } ?: return

        if (children.any { it.hasAttribute(SdkConstants.ATTR_LAYOUT_WEIGHT) }) return

        val vertical =
            element.getAttribute(SdkConstants.ATTR_ORIENTATION) == SdkConstants.VALUE_VERTICAL

        val drawableAttr = when (children.indexOf(imageView)) {
            0 -> if (vertical) SdkConstants.ATTR_DRAWABLE_TOP else SdkConstants.ATTR_DRAWABLE_LEFT
            1 -> if (vertical) SdkConstants.ATTR_DRAWABLE_BOTTOM else SdkConstants.ATTR_DRAWABLE_RIGHT
            else -> return
        }

        val message = buildString {
            append("This `LinearLayout` with an `ImageView` and a `TextView` ")
            append("can be more efficiently handled as a single `TextView` ")
            append("with a compound drawable (`$drawableAttr`). ")
            append("Any offset between the two widgets can be expressed with ")
            append("`android:drawablePadding`.")
        }

        context.report(ISSUE, element, context.getLocation(element), message)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more
                efficiently handled as a compound drawable (a single `TextView`, using the
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` attributes
                to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be replaced
                with a `drawablePadding` attribute.
            """,
            category = Category.PERFORMANCE,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}