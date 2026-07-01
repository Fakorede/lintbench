package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ScrollViewChildDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(
      com.android.SdkConstants.TAG_SCROLL_VIEW,
      com.android.SdkConstants.TAG_HORIZONTAL_SCROLL_VIEW
    )
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val isHorizontal = element.tagName == com.android.SdkConstants.TAG_HORIZONTAL_SCROLL_VIEW
    val dimensionAttr = if (isHorizontal) {
      com.android.SdkConstants.ATTR_LAYOUT_WIDTH
    } else {
      com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
    }

    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child !is org.w3c.dom.Element) {
        continue
      }

      val attr = child.getAttributeNodeNS(
        com.android.SdkConstants.ANDROID_URI,
        dimensionAttr
      ) ?: continue

      val value = attr.value
      if (value == com.android.SdkConstants.VALUE_FILL_PARENT ||
        value == com.android.SdkConstants.VALUE_MATCH_PARENT
      ) {
        val dimension = if (isHorizontal) "width" else "height"
        context.report(
          ISSUE,
          attr,
          context.getValueLocation(attr),
          "ScrollView children must set their layout_$dimension to wrap_content, " +
            "not $value, in the scrolling dimension"
        )
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ScrollViewSize",
      briefDescription = "ScrollView child uses match_parent/fill_parent in scrolling dimension",
      explanation = "ScrollView children must set their layout_width or layout_height " +
        "attributes to wrap_content rather than fill_parent or match_parent in the " +
        "scrolling dimension; otherwise the ScrollView may not scroll correctly.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(
        ScrollViewChildDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}