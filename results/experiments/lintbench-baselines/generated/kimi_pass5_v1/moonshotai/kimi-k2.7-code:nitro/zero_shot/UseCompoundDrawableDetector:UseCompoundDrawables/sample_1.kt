package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class UseCompoundDrawableDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "UseCompoundDrawables",
            "Node can be replaced by a `TextView` with compound drawables",
            """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more
                efficiently handled as a compound drawable (a single `TextView`, using the
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom`
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be
                replaced with a `drawablePadding` attribute.
            """.trimIndent(),
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            Implementation(UseCompoundDrawableDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )

        private const val MESSAGE = "This `LinearLayout` can be replaced by a single `TextView` with compound drawables"

        private val COMPOUND_DRAWABLE_ATTRIBUTES = listOf(
            SdkConstants.ATTR_DRAWABLE_LEFT,
            SdkConstants.ATTR_DRAWABLE_RIGHT,
            SdkConstants.ATTR_DRAWABLE_TOP,
            SdkConstants.ATTR_DRAWABLE_BOTTOM,
            SdkConstants.ATTR_DRAWABLE_START,
            SdkConstants.ATTR_DRAWABLE_END
        )
    }

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val children = (0 until element.childNodes.length)
            .map { element.childNodes.item(it) }
            .filterIsInstance<Element>()

        if (children.size != 2) return

        val first = children[0]
        val second = children[1]

        val hasImageView = first.tagName == SdkConstants.TAG_IMAGE_VIEW ||
                second.tagName == SdkConstants.TAG_IMAGE_VIEW
        val hasTextView = first.tagName == SdkConstants.TAG_TEXT_VIEW ||
                second.tagName == SdkConstants.TAG_TEXT_VIEW

        if (!hasImageView || !hasTextView) return

        val imageView = if (first.tagName == SdkConstants.TAG_IMAGE_VIEW) first else second
        val textView = if (first.tagName == SdkConstants.TAG_TEXT_VIEW) first else second

        if (!imageView.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SRC)) return
        if (textView.hasExistingCompoundDrawable()) return

        context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            MESSAGE
        )
    }

    private fun Element.hasExistingCompoundDrawable(): Boolean =
        COMPOUND_DRAWABLE_ATTRIBUTES.any { hasAttributeNS(SdkConstants.ANDROID_URI, it) }
}