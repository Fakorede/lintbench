package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class UseCompoundDrawableDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        val children = mutableListOf<Element>()
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                children.add(node)
            }
        }

        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]

        val firstTag = first.tagName
        val secondTag = second.tagName

        val firstIsImg = isImageView(firstTag)
        val firstIsTxt = isTextView(firstTag)
        val secondIsImg = isImageView(secondTag)
        val secondIsTxt = isTextView(secondTag)

        val imageView: Element
        val textView: Element

        if (firstIsImg && secondIsTxt) {
            imageView = first
            textView = second
        } else if (firstIsTxt && secondIsImg) {
            textView = first
            imageView = second
        } else {
            return
        }

        // Ignore if either has layout_weight
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT) ||
            textView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)
        ) {
            return
        }

        // Ignore if the ImageView has an ID (might be referenced in code)
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
            return
        }

        // Ignore if the ImageView has a background
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) {
            return
        }

        // Ignore if the ImageView has visibility set
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_VISIBILITY)) {
            return
        }

        // Ignore if the ImageView is clickable
        if (imageView.getAttributeNS(ANDROID_URI, "clickable") == "true" ||
            imageView.hasAttributeNS(ANDROID_URI, "onClick")
        ) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "This tag can be replaced with a `<TextView>` with compound drawables"
        )
    }

    private fun isImageView(tag: String): Boolean {
        return tag == IMAGE_VIEW || tag == "ImageView" || tag.endsWith(".ImageView") || tag == "AppCompatImageView" || tag.endsWith(".AppCompatImageView")
    }

    private fun isTextView(tag: String): Boolean {
        return tag == TEXT_VIEW || tag == "TextView" || tag.endsWith(".TextView") || tag == "AppCompatTextView" || tag.endsWith(".AppCompatTextView")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "LinearLayout can be replaced with a TextView",
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
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}