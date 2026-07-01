package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ChildCountDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(
      "AdapterView",
      "ListView",
      "GridView",
      "ExpandableListView",
      "Spinner",
      "Gallery",
      "AdapterViewFlipper",
      "StackView"
    )
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val children = element.childNodes
    for (i in 0 until children.length) {
      if (children.item(i).nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        context.report(
          ISSUE,
          element,
          context.getElementLocation(element),
          "An AdapterView such as a ListView cannot have children in XML"
        )
        return
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AdapterViewChildren",
      briefDescription = "AdapterView cannot have children in XML",
      explanation = "An AdapterView such as a ListView, GridView, or Spinner must be " +
        "configured with data from code, for example using a ListAdapter. Declaring " +
        "children in XML is not supported and will be ignored at runtime.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}