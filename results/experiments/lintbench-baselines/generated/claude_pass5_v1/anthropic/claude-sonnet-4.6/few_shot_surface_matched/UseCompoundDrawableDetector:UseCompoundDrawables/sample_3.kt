package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_CLICKABLE
import com.android.SdkConstants.ATTR_GRAVITY
import com.android.SdkConstants.ATTR_LAYOUT_GRAVITY
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
        // Count children and identify ImageView and TextView children
        var childCount = 0
        var imageViewChild: Element? = null
        var textViewChild: Element? = null

        var child = XmlUtils.getFirstSubTag(element)
        while (child != null) {
            childCount++
            when (child.tagName) {
                IMAGE_VIEW -> imageViewChild = child
                TEXT_VIEW -> textViewChild = child
                else -> {
                    // Any other child type means we can't do the conversion
                    child = XmlUtils.getNextTag(child)
                    continue
                }
            }
            child = XmlUtils.getNextTag(child)
        }

        // Must have exactly 2 children: one ImageView and one TextView
        if (childCount != 2 || imageViewChild == null || textViewChild == null) {
            return
        }

        // If the LinearLayout has a background, skip (compound drawables don't support this easily)
        if (element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) {
            return
        }

        // If the LinearLayout is clickable, skip
        if (element.hasAttributeNS(ANDROID_URI, ATTR_CLICKABLE)) {
            val clickable = element.getAttributeNS(ANDROID_URI, ATTR_CLICKABLE)
            if (clickable == "true") {
                return
            }
        }

        // If the ImageView has a layout_weight, skip
        if (imageViewChild.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return
        }

        // If the TextView has a layout_weight, skip
        if (textViewChild.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return
        }

        // If the ImageView has a non-center scale type that would be lost, skip
        if (imageViewChild.hasAttributeNS(ANDROID_URI, ATTR_SCALE_TYPE)) {
            val scaleType = imageViewChild.getAttributeNS(ANDROID_URI, ATTR_SCALE_TYPE)
            if (scaleType != "center") {
                return
            }
        }

        // If the ImageView has layout_gravity set, skip
        if (imageViewChild.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY)) {
            return
        }

        // If the TextView has layout_gravity set, skip
        if (textViewChild.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY)) {
            return
        }

        // If the TextView has a gravity set that's not compatible, skip
        if (textViewChild.hasAttributeNS(ANDROID_URI, ATTR_GRAVITY)) {
            val gravity = textViewChild.getAttributeNS(ANDROID_URI, ATTR_GRAVITY)
            if (gravity.contains("fill")) {
                return
            }
        }

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