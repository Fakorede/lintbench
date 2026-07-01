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
        // Count the direct children and check that we have exactly one ImageView and one TextView
        var imageViewCount = 0
        var textViewCount = 0
        var otherCount = 0

        var child = XmlUtils.getFirstSubTag(element)
        while (child != null) {
            when (child.tagName) {
                IMAGE_VIEW -> imageViewCount++
                TEXT_VIEW -> textViewCount++
                else -> otherCount++
            }
            child = XmlUtils.getNextTag(child)
        }

        if (imageViewCount == 1 && textViewCount == 1 && otherCount == 0) {
            // Check that neither child has a layout_weight set, which would complicate replacement
            var hasWeight = false
            var hasBackground = false

            // Check if the LinearLayout itself has a background (may be harder to replicate)
            // We still report in this case but it's worth noting
            // Actually we just check children for weights and the ImageView for unsupported attributes

            var imageView: Element? = null
            child = XmlUtils.getFirstSubTag(element)
            while (child != null) {
                val weight = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)
                if (!weight.isNullOrEmpty()) {
                    hasWeight = true
                }
                if (child.tagName == IMAGE_VIEW) {
                    imageView = child
                }
                child = XmlUtils.getNextTag(child)
            }

            if (hasWeight) {
                return
            }

            // Check if the ImageView has a scaleType that isn't compatible
            if (imageView != null) {
                val scaleType = imageView.getAttributeNS(ANDROID_URI, ATTR_SCALE_TYPE)
                if (!scaleType.isNullOrEmpty()) {
                    // compound drawables don't support scaleType
                    return
                }

                // Check if the ImageView has a background
                val imageBackground = imageView.getAttributeNS(ANDROID_URI, ATTR_BACKGROUND)
                if (!imageBackground.isNullOrEmpty()) {
                    return
                }
            }

            // Check if the LinearLayout has a background — compound drawables can't replicate
            // complex backgrounds easily, but we still warn since it may still be applicable
            val background = element.getAttributeNS(ANDROID_URI, ATTR_BACKGROUND)
            if (!background.isNullOrEmpty()) {
                // Still valid to report — compound drawable on the TextView can have background
                // but let's skip to avoid false positives
                return
            }

            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "This tag and its children can be replaced by one `<TextView/>` and " +
                    "a compound drawable"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation =
                "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
                    "efficiently handled as a compound drawable (a single `TextView`, using the " +
                    "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` " +
                    "attributes to draw one or more images adjacent to the text).\n\n" +
                    "If the two widgets are offset from each other with margins, this can be " +
                    "replaced with a `drawablePadding` attribute.\n\n" +
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