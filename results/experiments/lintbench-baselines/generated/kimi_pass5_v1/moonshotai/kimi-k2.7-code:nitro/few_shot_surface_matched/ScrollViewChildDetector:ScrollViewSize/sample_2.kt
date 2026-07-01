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
private const val VALUE_FILL_PARENT = "fill_parent"
private const val VALUE_MATCH_PARENT = "match_parent"

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
  }

  override fun getIssues(): List<Issue> = listOf(ISSUE)

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val isHorizontal = element.tagName == HORIZONTAL_SCROLL_VIEW
    val attrName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT
    val dimension = if (isHorizontal) "width" else "height"

    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i) as? org.w3c.dom.Element ?: continue
      val attr = child.getAttributeNodeNS(ANDROID_URI, attrName) ?: continue
      val value = attr.value
      if (value == VALUE_FILL_PARENT || value == VALUE_MATCH_PARENT) {
        context.report(
          ISSUE,
          attr,
          context.getValueLocation(attr),
          "ScrollView children should set their layout_$dimension to wrap_content, " +
            "not fill_parent or match_parent"
        )
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ScrollViewSize",
      briefDescription = "ScrollView child size should be wrap_content in scroll dimension",
      explanation = "ScrollView children must set their layout_width or layout_height " +
        "attribute to wrap_content (rather than fill_parent or match_parent) in the " +
        "scrolling dimension; otherwise scrolling and measurement may not work correctly.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(
        ScrollViewChildDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      ),
    )
  }
}