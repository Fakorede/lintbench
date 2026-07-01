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

class ChildCountDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(
      "AdapterView",
      "ListView",
      "GridView",
      "ExpandableListView",
      "Spinner",
      "Gallery"
    )
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val children = element.childNodes
    for (i in 0 until children.length) {
      if (children.item(i).nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        val tag = element.tagName.substringAfterLast('.')
        context.report(
          ISSUE,
          element,
          context.getLocation(element),
          "$tag is an AdapterView and cannot have children in XML"
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
      explanation = "AdapterView subclasses such as ListView, GridView, Spinner and " +
        "ExpandableListView must be configured with an adapter from code. They cannot " +
        "contain child views in the XML layout.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(ChildCountDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}