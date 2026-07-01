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

class NamespaceDetector : ResourceXmlDetector() {

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement ?: return
    val childNodes = root.childNodes ?: return
    for (i in 0 until childNodes.length) {
      val child = childNodes.item(i) ?: continue
      if (child is Element) {
        checkElement(context, child)
      }
    }
  }

  private fun checkElement(context: XmlContext, element: Element) {
    val attributes = element.attributes ?: return
    for (i in 0 until attributes.length) {
      val attribute = attributes.item(i) ?: continue
      val name = attribute.nodeName ?: continue
      if (name.startsWith("xmlns:") || name == "xmlns") {
        context.report(
          ISSUE,
          attribute,
          context.getLocation(attribute),
          "Redundant namespace declaration"
        )
      }
    }
    val childNodes = element.childNodes ?: return
    for (i in 0 until childNodes.length) {
      val child = childNodes.item(i) ?: continue
      if (child is Element) {
        checkElement(context, child)
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "RedundantNamespace",
      briefDescription = "Redundant namespace",
      explanation = "In Android XML documents, only specify the namespace on the root/document " +
        "element. Namespace declarations elsewhere in the document are typically " +
        "accidental leftovers from copy/pasting XML from other files or documentation.",
      category = Category.PERFORMANCE,
      priority = 3,
      severity = Severity.WARNING,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
    )
  }
}