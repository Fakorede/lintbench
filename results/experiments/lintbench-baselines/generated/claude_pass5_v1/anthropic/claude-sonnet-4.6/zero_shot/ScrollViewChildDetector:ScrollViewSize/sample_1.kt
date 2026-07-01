package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.FILL_PARENT
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.MATCH_PARENT
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
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = """
                ScrollView children must set their `layout_width` or `layout_height` attributes \
                to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling \
                dimension.
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val FILL_VALUES = setOf(VALUE_FILL_PARENT, VALUE_MATCH_PARENT)
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == HORIZONTAL_SCROLL_VIEW

        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                val child = node
                if (isHorizontal) {
                    val widthAttr = child.getAttributeNS(
                        "http://schemas.android.com/apk/res/android",
                        ATTR_LAYOUT_WIDTH
                    )
                    if (widthAttr in FILL_VALUES) {
                        val attrNode = child.getAttributeNodeNS(
                            "http://schemas.android.com/apk/res/android",
                            ATTR_LAYOUT_WIDTH
                        )
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(attrNode ?: child),
                            "This child view of a `HorizontalScrollView` sets its `layout_width` " +
                                    "to `${widthAttr}` which is not useful"
                        )
                    }
                } else {
                    val heightAttr = child.getAttributeNS(
                        "http://schemas.android.com/apk/res/android",
                        ATTR_LAYOUT_HEIGHT
                    )
                    if (heightAttr in FILL_VALUES) {
                        val attrNode = child.getAttributeNodeNS(
                            "http://schemas.android.com/apk/res/android",
                            ATTR_LAYOUT_HEIGHT
                        )
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(attrNode ?: child),
                            "This child view of a `ScrollView` sets its `layout_height` " +
                                    "to `${heightAttr}` which is not useful"
                        )
                    }
                }
            }
        }
    }
}