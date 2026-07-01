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
    val unusedNamespaces = mutableListOf<org.w3c.dom.Attr>()
    val usedPrefixes = mutableSetOf<String>()

    fun visit(node: org.w3c.dom.Node) {
      if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        val element = node as org.w3c.dom.Element
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
          val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
          val name = attr.name
          if (name == "xmlns" || name.startsWith("xmlns:")) {
            unusedNamespaces.add(attr)
          } else {
            attr.prefix?.let { usedPrefixes.add(it) }
          }
        }
        node.prefix?.let { usedPrefixes.add(it) }
      }
      val children = node.childNodes
      for (i in 0 until children.length) {
        visit(children.item(i))
      }
    }

    visit(document)

    for (attr in unusedNamespaces) {
      val prefix = attr.name.removePrefix("xmlns:")
      if (prefix !in usedPrefixes) {
        context.report(
          ISSUE,
          attr,
          context.getLocation(attr),
          "Unused namespace declaration: `$prefix`"
        )
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "UnusedNamespace",
      briefDescription = "Unused namespace",
      explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
      category = Category.PERFORMANCE,
      priority = 3,
      severity = Severity.WARNING,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
    )
  }
}