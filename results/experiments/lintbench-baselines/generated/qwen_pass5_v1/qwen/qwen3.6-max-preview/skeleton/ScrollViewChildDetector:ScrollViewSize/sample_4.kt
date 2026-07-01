package com.android.tools.lint.checks

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
            explanation = "ScrollView children must set their layout_width or layout_height attributes to wrap_content rather than fill_parent or match_parent in the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("ScrollView", "HorizontalScrollView")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == "HorizontalScrollView"
        val attrName = if (isHorizontal) "layout_width" else "layout_height"
        val androidNs = "http://schemas.android.com/apk/res/android"

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                val attr = child.getAttributeNodeNS(androidNs, attrName)
                if (attr != null) {
                    val value = attr.value
                    if (value == "match_parent" || value == "fill_parent") {
                        val message = "This ${child.tagName} should use android:$attrName=\"wrap_content\" rather than \"$value\" because it is inside a ${element.tagName}"
                        context.report(ISSUE, child, context.getLocation(attr), message)
                    }
                }
            }
        }
    }
}