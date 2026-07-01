package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement ?: return
    checkElement(context, root, root)
  }

  private fun checkElement(context: XmlContext, element: Element, root: Element) {
    if (element !== root) {
      val attributes = element.attributes
      for (i in 0 until attributes.length) {
        val attr = attributes.item(i) as? Attr ?: continue
        val name = attr.name
        if (name == "xmlns" || name.startsWith("xmlns:")) {
          context.report(
            ISSUE,
            attr,
            context.getLocation(attr),
            "Redundant namespace declaration `$name`; namespace declarations should only be on the root element"
          )
        }
      }
    }

    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == Node.ELEMENT_NODE) {
        checkElement(context, child as Element, root)
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