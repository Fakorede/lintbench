package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Lint
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector() {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"
        private const val VALUE_FILL_PARENT = "fill_parent"
        private const val VALUE_MATCH_PARENT = "match_parent"
        private const val SCROLL_VIEW = "ScrollView"
        private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"

        private val IMPLEMENTATION = Implementation(
            ScrollViewChildDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = """
                A ScrollView can only scroll its content if the content is larger than the
                ScrollView itself. If the child of a vertical ScrollView sets
                `android:layout_height` to `match_parent` or `fill_parent`, it will be as tall
                as the ScrollView and there will be nothing to scroll. The child should use
                `wrap_content` instead. Similarly, the child of a HorizontalScrollView should
                use `wrap_content` for `android:layout_width`.
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(
        SCROLL_VIEW,
        HORIZONTAL_SCROLL_VIEW,
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = HORIZONTAL_SCROLL_VIEW == element.tagName
        val attrName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        for (child in Lint.getChildren(element)) {
            val attribute: Attr = child.getAttributeNodeNS(ANDROID_URI, attrName) ?: continue
            val value = attribute.value

            if (value == VALUE_FILL_PARENT || value == VALUE_MATCH_PARENT) {
                val message = "The $attrName of this ${element.tagName}'s child should " +
                    "be \"wrap_content\", not \"$value\"; otherwise it cannot scroll."
                context.report(ISSUE, child, context.getValueLocation(attribute), message)
            }
        }
    }
}