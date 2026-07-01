package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_DIVIDER
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_SHOW_DIVIDERS
import com.android.SdkConstants.ATTR_WEIGHT_SUM
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.LintUtils
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class UseCompoundDrawableDetector : LayoutDetector() {
    override fun getApplicableElements(): Collection<String>? = listOf(LINEAR_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_WEIGHT_SUM) ||
            element.hasAttributeNS(ANDROID_URI, ATTR_DIVIDER) ||
            element.hasAttributeNS(ANDROID_URI, ATTR_SHOW_DIVIDERS)) {
            return
        }

        var imageView: Element? = null
        var textView: Element? = null
        var childCount = 0

        for (child in LintUtils.getChildren(element)) {
            childCount++
            val tag = child.tagName
            if (tag == IMAGE_VIEW || tag.endsWith(".ImageView")) {
                imageView = child
            } else if (tag == TEXT_VIEW || tag.endsWith(".TextView")) {
                textView = child
            }
        }

        if (childCount != 2 || imageView == null || textView == null) {
            return
        }

        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT) ||
            textView.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This tag and its children can be replaced by one <TextView/> and a compound drawable"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "UseCompoundDrawables",
            "Node can be replaced by a `TextView` with compound drawables",
            "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
            "efficiently handled as a compound drawable (a single TextView, using the " +
            "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` " +
            "attributes to draw one or more images adjacent to the text).\n" +
            "\n" +
            "If the two widgets are offset from each other with margins, this can be " +
            "replaced with a `drawablePadding` attribute.\n" +
            "\n" +
            "There's a lint quickfix to perform this conversion in the Eclipse plugin.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            Implementation(UseCompoundDrawableDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}