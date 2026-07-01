package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.SCROLL_VIEW
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ScrollViewChildDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = "ScrollView children must set their `layout_width` or `layout_height` " +
                "attributes to `wrap_content` rather than `fill_parent` or `match_parent` in " +
                "the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private val FILL_PARENT_VALUES = setOf(VALUE_FILL_PARENT, VALUE_MATCH_PARENT)
    }

    override fun getApplicableElements(): Collection<String> = listOf(
        SCROLL_VIEW,
        HORIZONTAL_SCROLL_VIEW,
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == HORIZONTAL_SCROLL_VIEW

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child !is Element) {
                continue
            }

            if (isHorizontal) {
                // HorizontalScrollView scrolls horizontally, so layout_width must be wrap_content
                val widthValue = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
                if (widthValue in FILL_PARENT_VALUES) {
                    val attr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(attr ?: child),
                        "This child of a `HorizontalScrollView` should set its `layout_width` " +
                            "to `wrap_content`, not `match_parent` or `fill_parent`",
                    )
                }
            } else {
                // ScrollView scrolls vertically, so layout_height must be wrap_content
                val heightValue = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
                if (heightValue in FILL_PARENT_VALUES) {
                    val attr = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)
                    context.report(
                        ISSUE,
                        child,
                        context.getLocation(attr ?: child),
                        "This child of a `ScrollView` should set its `layout_height` " +
                            "to `wrap_content`, not `match_parent` or `fill_parent`",
                    )
                }
            }
        }
    }
}