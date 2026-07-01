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
    val declaredPrefixes = mutableMapOf<String, org.w3c.dom.Attr>()

    val rootAttributes = root.attributes
    for (i in 0 until rootAttributes.length) {
      val attr = rootAttributes.item(i) as? org.w3c.dom.Attr ?: continue
      val name = attr.name
      if (name.startsWith("xmlns:")) {
        val prefix = name.substring("xmlns:".length)
        declaredPrefixes[prefix] = attr
      }
    }

    if (declaredPrefixes.isEmpty()) return

    val usedPrefixes = mutableSetOf<String>()
    collectUsedPrefixes(root, usedPrefixes)

    for ((prefix, attr) in declaredPrefixes) {
      if (prefix !in usedPrefixes) {
        context.report(
          ISSUE,
          attr,
          context.getLocation(attr),
          "Unused namespace `$prefix`"
        )
      }
    }
  }

  private fun collectUsedPrefixes(node: org.w3c.dom.Node, usedPrefixes: MutableSet<String>) {
    if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) return

    node.prefix?.let { usedPrefixes.add(it) }

    val element = node as org.w3c.dom.Element
    val attributes = element.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) ?: continue
      attr.prefix?.let { usedPrefixes.add(it) }
    }

    var child = node.firstChild
    while (child != null) {
      collectUsedPrefixes(child, usedPrefixes)
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
      priority = 3,
      severity = Severity.WARNING,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}