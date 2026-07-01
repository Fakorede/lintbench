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

    val declaredNamespaces = mutableMapOf<String, org.w3c.dom.Attr>()
    val rootAttributes = root.attributes
    for (i in 0 until rootAttributes.length) {
      val attr = rootAttributes.item(i) as? org.w3c.dom.Attr ?: continue
      val prefix = attr.prefix
      if (prefix == "xmlns" || attr.name == "xmlns") {
        declaredNamespaces[attr.value] = attr
      }
    }

    if (declaredNamespaces.isEmpty()) {
      return
    }

    val usedNamespaces = mutableSetOf<String>()
    collectUsedNamespaces(root, usedNamespaces)

    for ((uri, attr) in declaredNamespaces) {
      if (uri !in usedNamespaces) {
        context.report(
          ISSUE,
          attr,
          context.getValueLocation(attr),
          "Unused namespace `$uri`"
        )
      }
    }
  }

  private fun collectUsedNamespaces(node: org.w3c.dom.Node, used: MutableSet<String>) {
    if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) {
      return
    }

    val element = node as org.w3c.dom.Element
    element.namespaceURI?.let { used.add(it) }

    val attributes = element.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
      attr.namespaceURI?.let { used.add(it) }
    }

    var child = element.firstChild
    while (child != null) {
      collectUsedNamespaces(child, used)
      child = child.nextSibling
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "UnusedNamespace",
      briefDescription = "Unused namespace",
      explanation = "Unused namespace declarations take up space and require processing that is not necessary.",
      category = Category.PERFORMANCE,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
    )
  }
}