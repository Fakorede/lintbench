package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_SCALE_TYPE
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class UseCompoundDrawableDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Count children and identify if we have exactly one ImageView and one TextView
        var imageViewCount = 0
        var textViewCount = 0
        var otherCount = 0

        var child = XmlUtils.getFirstSubTag(element)
        while (child != null) {
            val tag = child.tagName
            when {
                tag == IMAGE_VIEW -> imageViewCount++
                tag == TEXT_VIEW -> textViewCount++
                else -> otherCount++
            }
            child = XmlUtils.getNextTag(child)
        }

        if (imageViewCount == 1 && textViewCount == 1 && otherCount == 0) {
            // Check that the LinearLayout itself doesn't have a background, which would
            // make it harder to convert (the TextView would need to absorb it)
            // We still warn but let the developer decide.

            // Check that neither child uses layout_weight (which can't be expressed with
            // compound drawables)
            var hasWeight = false
            var imageView: Element? = null
            var textView: Element? = null

            child = XmlUtils.getFirstSubTag(element)
            while (child != null) {
                val tag = child.tagName
                if (tag == IMAGE_VIEW) {
                    imageView = child
                } else if (tag == TEXT_VIEW) {
                    textView = child
                }
                if (child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                    hasWeight = true
                }
                child = XmlUtils.getNextTag(child)
            }

            if (hasWeight) {
                return
            }

            // If the ImageView has a non-default scaleType, skip (compound drawables
            // don't support scaleType)
            if (imageView != null && imageView.hasAttributeNS(ANDROID_URI, ATTR_SCALE_TYPE)) {
                return
            }

            // If the LinearLayout has a background, compound drawables may not be a
            // straightforward replacement, but we still report the issue
            val background = element.getAttributeNS(ANDROID_URI, ATTR_BACKGROUND)

            // If the ImageView itself has a background, skip
            if (imageView != null && imageView.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) {
                return
            }

            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This tag and its children can be replaced by one `<TextView/>` and " +
                    "a compound drawable"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation =
                "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
                    "efficiently handled as a compound drawable (a single `TextView`, using the " +
                    "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` " +
                    "attributes to draw one or more images adjacent to the text).\n" +
                    "\n" +
                    "If the two widgets are offset from each other with margins, this can be " +
                    "replaced with a `drawablePadding` attribute.\n" +
                    "\n" +
                    "There's a lint quickfix to perform this conversion in the Eclipse plugin.",
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