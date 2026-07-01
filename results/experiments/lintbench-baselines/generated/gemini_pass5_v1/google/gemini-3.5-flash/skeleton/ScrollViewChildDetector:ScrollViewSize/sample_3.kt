package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.LayoutDetector
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
            explanation = """
                ScrollView children should set their `layout_width` or `layout_height` \
                attributes to `wrap_content` rather than `fill_parent` or `match_parent` \
                in the scrolling dimension. Otherwise, the ScrollView cannot scroll \
                properly because the child is forced to be the same size as the ScrollView.
                """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_LAYOUT_WIDTH = "layout_width"
        private const val ATTR_LAYOUT_HEIGHT = "layout_height"
        private const val VALUE_MATCH_PARENT = "match_parent"
        private const val VALUE_FILL_PARENT = "fill_parent"
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("ScrollView", "HorizontalScrollView")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == "HorizontalScrollView"
        val targetAttribute = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val attr = child.getAttributeNodeNS(ANDROID_URI, targetAttribute)
                if (attr != null) {
                    val value = attr.value
                    if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
                        val msg = if (isHorizontal) {
                            "HorizontalScrollView children should set `android:layout_width` to `wrap_content` rather than `$value`"
                        } else {
                            "ScrollView children should set `android:layout_height` to `wrap_content` rather than `$value`"
                        }
                        context.report(
                            ISSUE,
                            attr,
                            context.getLocation(attr),
                            msg
                        )
                    }
                }
            }
        }
    }
}