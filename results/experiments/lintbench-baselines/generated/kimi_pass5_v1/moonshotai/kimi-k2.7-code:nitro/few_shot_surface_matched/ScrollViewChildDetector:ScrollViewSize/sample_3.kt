package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val SCROLL_VIEW = "ScrollView"
private const val HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"
private const val ATTR_LAYOUT_WIDTH = "layout_width"
private const val ATTR_LAYOUT_HEIGHT = "layout_height"
private const val VALUE_MATCH_PARENT = "match_parent"
private const val VALUE_FILL_PARENT = "fill_parent"

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val tagName = element.tagName
    val isHorizontal = tagName == HORIZONTAL_SCROLL_VIEW
    val attrName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT
    val dimension = if (isHorizontal) "width" else "height"

    val childNodes = element.childNodes
    for (i in 0 until childNodes.length) {
      val node = childNodes.item(i)
      if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
        continue
      }
      val child = node as org.w3c.dom.Element
      val attr = child.getAttributeNodeNS(ANDROID_URI, attrName) ?: continue
      val value = attr.value
      if (value == VALUE_MATCH_PARENT || value == VALUE_FILL_PARENT) {
        context.report(
          ISSUE,
          attr,
          context.getValueLocation(attr),
          "ScrollView child should set android:${attrName} to wrap_content, not ${value}, " +
            "in the scrolling dimension (${dimension})"
        )
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ScrollViewSize",
      briefDescription = "ScrollView child uses match_parent in scrolling dimension",
      explanation = "ScrollView children should set their layout_width or layout_height to " +
        "wrap_content rather than match_parent or fill_parent in the scrolling dimension.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(ScrollViewChildDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}