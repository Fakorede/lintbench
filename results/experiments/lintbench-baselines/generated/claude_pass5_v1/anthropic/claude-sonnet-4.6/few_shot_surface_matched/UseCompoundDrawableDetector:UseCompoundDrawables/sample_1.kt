package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_ORIENTATION
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.VALUE_VERTICAL
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils.getFirstSubTag
import com.android.utils.XmlUtils.getNextTag
import org.w3c.dom.Element

class UseCompoundDrawableDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String> {
        return listOf(LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Count children and identify if they are exactly one ImageView and one TextView
        var childCount = 0
        var hasImageView = false
        var hasTextView = false
        var hasOther = false

        var child = getFirstSubTag(element)
        while (child != null) {
            childCount++
            val tag = child.tagName
            when {
                tag == IMAGE_VIEW -> hasImageView = true
                tag == TEXT_VIEW -> hasTextView = true
                else -> hasOther = true
            }
            child = getNextTag(child)
        }

        if (childCount != 2 || !hasImageView || !hasTextView || hasOther) {
            return
        }

        // Check that the LinearLayout itself does not have a background (which would be lost
        // in the conversion), and that neither child has a layout_weight set.
        val background = element.getAttributeNS(ANDROID_URI, ATTR_BACKGROUND)
        if (!background.isNullOrEmpty()) {
            return
        }

        // Check children for layout_weight usage (which can't be expressed as compound drawable)
        child = getFirstSubTag(element)
        while (child != null) {
            val weight = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)
            if (!weight.isNullOrEmpty()) {
                return
            }
            child = getNextTag(child)
        }

        // Determine orientation; default is horizontal
        val orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION)
        // Both horizontal and vertical are candidates; the suggestion applies to both

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This tag and its children can be replaced by one `<TextView/>` and " +
                "a compound drawable"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation =
                "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
                    "efficiently handled as a compound drawable (a single `TextView`, using the " +
                    "`drawableTop`, `drawableLeft`, `drawableRight`, and/or `drawableBottom` " +
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