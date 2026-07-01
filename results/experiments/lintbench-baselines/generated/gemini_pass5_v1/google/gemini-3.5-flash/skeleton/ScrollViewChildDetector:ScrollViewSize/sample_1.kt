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
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

        private val IMPLEMENTATION = Implementation(
            ScrollViewChildDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = "ScrollView children must set their `layout_width` or `layout_height` attributes to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling dimension.",
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "ScrollView",
            "HorizontalScrollView",
            "androidx.core.widget.NestedScrollView",
            "android.widget.ScrollView",
            "android.widget.HorizontalScrollView"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val isHorizontal = tagName == "HorizontalScrollView" || tagName.endsWith(".HorizontalScrollView")
        val targetAttr = if (isHorizontal) "layout_width" else "layout_height"

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val childNode = childNodes.item(i)
            if (childNode is Element) {
                val attrNode = childNode.getAttributeNodeNS(ANDROID_URI, targetAttr)
                if (attrNode != null) {
                    val value = attrNode.value
                    if (value == "match_parent" || value == "fill_parent") {
                        val location = context.getLocation(attrNode)
                        context.report(
                            ISSUE,
                            attrNode,
                            location,
                            "ScrollView children should set `android:$targetAttr` to `wrap_content` rather than `$value`"
                        )
                    }
                }
            }
        }
    }
}