package com.android.tools.lint.checks

import com.android.tools.lint.checks.LayoutDetector
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ChildCountDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(
      "ListView",
      "android.widget.ListView",
      "GridView",
      "android.widget.GridView",
      "Spinner",
      "android.widget.Spinner",
      "ExpandableListView",
      "android.widget.ExpandableListView",
      "Gallery",
      "android.widget.Gallery",
      "StackView",
      "android.widget.StackView",
      "AdapterView",
      "android.widget.AdapterView"
    )
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val children = element.childNodes
    for (i in 0 until children.length) {
      if (children.item(i) is Element) {
        context.report(
          ISSUE,
          element,
          context.getElementLocation(element),
          "AdapterView cannot have children in XML"
        )
        return
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AdapterViewChildren",
      briefDescription = "AdapterViews cannot have children in XML",
      explanation = "An AdapterView such as a ListView must be configured with data from " +
        "Java code, such as a ListAdapter. It cannot have child views in XML.",
      category = Category.CORRECTNESS,
      priority = 7,
      severity = Severity.WARNING,
      implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}