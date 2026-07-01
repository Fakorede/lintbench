package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.DomUtils
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.Implementation
import org.w3c.dom.Element
import java.util.EnumSet

class UseCompoundDrawableDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a TextView with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a `drawablePadding` attribute.

                There's a lint quickfix to perform this conversion in the Eclipse plugin.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE)
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(SdkConstants.TAG_LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = DomUtils.getChildren(element)
        if (children.size != 2) return

        val first = children[0]
        val second = children[1]

        val firstTag = first.tagName
        val secondTag = second.tagName

        val hasImageView = firstTag == SdkConstants.TAG_IMAGE_VIEW || secondTag == SdkConstants.TAG_IMAGE_VIEW
        val hasTextView = firstTag == SdkConstants.TAG_TEXT_VIEW || secondTag == SdkConstants.TAG_TEXT_VIEW

        if (!hasImageView || !hasTextView) return

        val imageView = if (firstTag == SdkConstants.TAG_IMAGE_VIEW) first else second
        val textView = if (firstTag == SdkConstants.TAG_TEXT_VIEW) first else second

        val orientation = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ORIENTATION)
        val isVertical = orientation == SdkConstants.VALUE_VERTICAL
        val imageFirst = imageView == first

        val compoundAttr = when {
            isVertical && imageFirst -> SdkConstants.ATTR_DRAWABLE_TOP
            isVertical && !imageFirst -> SdkConstants.ATTR_DRAWABLE_BOTTOM
            !isVertical && imageFirst -> SdkConstants.ATTR_DRAWABLE_LEFT
            else -> SdkConstants.ATTR_DRAWABLE_RIGHT
        }

        val hasRelevantMargin = when (compoundAttr) {
            SdkConstants.ATTR_DRAWABLE_TOP -> {
                imageView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM).isNotEmpty() ||
                textView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP).isNotEmpty()
            }
            SdkConstants.ATTR_DRAWABLE_BOTTOM -> {
                imageView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP).isNotEmpty() ||
                textView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM).isNotEmpty()
            }
            SdkConstants.ATTR_DRAWABLE_LEFT -> {
                imageView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT).isNotEmpty() ||
                textView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT).isNotEmpty()
            }
            else -> {
                imageView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_LEFT).isNotEmpty() ||
                textView.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT).isNotEmpty()
            }
        }

        val message = if (hasRelevantMargin) {
            "This tag and its children can be replaced by one <TextView/> and a compound drawable (using drawablePadding for the margin)"
        } else {
            "This tag and its children can be replaced by one <TextView/> and a compound drawable"
        }

        context.report(ISSUE, element, context.getLocation(element), message)
    }
}