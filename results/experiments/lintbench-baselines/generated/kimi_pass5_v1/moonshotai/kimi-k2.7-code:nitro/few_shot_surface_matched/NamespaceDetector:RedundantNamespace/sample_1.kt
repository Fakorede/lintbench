package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

  override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
    val root = document.documentElement ?: return
    visitElement(context, root, root)
  }

  private fun visitElement(
    context: XmlContext,
    root: org.w3c.dom.Element,
    element: org.w3c.dom.Element
  ) {
    if (element !== root) {
      val attributes = element.attributes
      for (i in 0 until attributes.length) {
        val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
        if (attr.prefix == "xmlns" || attr.name == "xmlns") {
          context.report(
            ISSUE,
            attr,
            context.getLocation(attr),
            "Redundant namespace declaration; namespace should only be specified on the root element"
          )
        }
      }
    }

    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        visitElement(context, root, child as org.w3c.dom.Element)
      }
      child = child.nextSibling
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "RedundantNamespace",
      briefDescription = "Redundant namespace",
      explanation = "In Android XML documents, only specify the namespace on the root/document element. " +
        "Namespace declarations elsewhere in the document are typically accidental leftovers from copy/pasting XML from other files or documentation.",
      category = Category.PERFORMANCE,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}