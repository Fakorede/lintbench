package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("ScrollView", "HorizontalScrollView")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val isHorizontal = tagName == "HorizontalScrollView"
        val targetAttr = if (isHorizontal) "layout_width" else "layout_height"
        val androidUri = "http://schemas.android.com/apk/res/android"

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                val attribute = child.getAttributeNodeNS(androidUri, targetAttr)
                if (attribute != null) {
                    val value = attribute.value
                    if (value == "match_parent" || value == "fill_parent") {
                        val msg = if (isHorizontal) {
                            "HorizontalScrollView children should set `android:layout_width=\"wrap_content\"`"
                        } else {
                            "ScrollView children should set `android:layout_height=\"wrap_content\"`"
                        }
                        context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            msg
                        )
                    }
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = "ScrollView children must set their `layout_width` or `layout_height` attributes to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}