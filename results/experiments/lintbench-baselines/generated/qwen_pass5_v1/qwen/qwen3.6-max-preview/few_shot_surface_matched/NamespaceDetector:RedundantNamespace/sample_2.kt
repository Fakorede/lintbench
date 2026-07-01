package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement ?: return
    checkElement(context, root, isRoot = true)
  }

  private fun checkElement(context: XmlContext, element: Element, isRoot: Boolean) {
    if (!isRoot) {
      val attributes = element.attributes
      for (i in 0 until attributes.length) {
        val attr = attributes.item(i)
        val name = attr.nodeName
        if (name.startsWith("xmlns") || attr.namespaceURI == "http://www.w3.org/2000/xmlns/") {
          context.report(
            ISSUE,
            attr,
            context.getValueLocation(attr),
            "Redundant namespace declaration: only declare namespaces on the root element"
          )
        }
      }
    }

    var child: Node? = element.firstChild
    while (child != null) {
      if (child.nodeType == Node.ELEMENT_NODE) {
        checkElement(context, child as Element, isRoot = false)
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
        "Namespace declarations elsewhere in the document are typically accidental leftovers from " +
        "copy/pasting XML from other files or documentation.",
      category = Category.PERFORMANCE,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}