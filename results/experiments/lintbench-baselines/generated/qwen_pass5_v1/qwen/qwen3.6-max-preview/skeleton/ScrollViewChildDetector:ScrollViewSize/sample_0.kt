package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
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

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("ScrollView", "HorizontalScrollView")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.localName ?: element.tagName
        val isHorizontal = tagName == "HorizontalScrollView"
        val attrName = if (isHorizontal) "layout_width" else "layout_height"
        val dimension = if (isHorizontal) "width" else "height"

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val childElement = child as Element

            val attr = childElement.getAttributeNodeNS(ANDROID_URI, attrName)
            if (attr != null) {
                val value = attr.value
                if (value == "match_parent" || value == "fill_parent") {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "This child should set `android:layout_$dimension` to `wrap_content` rather than `$value` in a scrolling dimension"
                    )
                }
            }
        }
    }
}