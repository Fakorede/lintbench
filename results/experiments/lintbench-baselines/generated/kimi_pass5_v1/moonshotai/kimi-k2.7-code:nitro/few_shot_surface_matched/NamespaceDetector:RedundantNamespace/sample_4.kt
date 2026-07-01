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
    checkElement(context, root, root)
  }

  private fun checkElement(
    context: XmlContext,
    element: org.w3c.dom.Element,
    root: org.w3c.dom.Element
  ) {
    if (element !== root) {
      val attributes = element.attributes
      for (i in 0 until attributes.length) {
        val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
        if (attr.namespaceURI == XMLNS_URI ||
            attr.name == "xmlns" ||
            attr.name.startsWith("xmlns:")
        ) {
          context.report(
            ISSUE,
            attr,
            context.getLocation(attr),
            "Redundant namespace declaration on non-root element"
          )
        }
      }
    }

    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        checkElement(context, child as org.w3c.dom.Element, root)
      }
      child = child.nextSibling
    }
  }

  companion object {
    private const val XMLNS_URI = "http://www.w3.org/2000/xmlns/"

    @JvmField
    val ISSUE = Issue.create(
      id = "RedundantNamespace",
      briefDescription = "Redundant namespace declaration",
      explanation = "In Android XML documents, only specify the namespace on the root element. " +
        "Namespace declarations elsewhere in the document are typically accidental leftovers " +
        "from copy/pasting XML from other files or documentation.",
      category = Category.PERFORMANCE,
      priority = 3,
      severity = Severity.WARNING,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}