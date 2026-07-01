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
    val usedPrefixes = HashSet<String>()
    val declarations = mutableListOf<Pair<org.w3c.dom.Attr, String>>()

    fun visit(node: org.w3c.dom.Node) {
      if (node is org.w3c.dom.Element) {
        extractPrefix(node.nodeName)?.let { usedPrefixes.add(it) }
        val attrs = node.attributes
        for (i in 0 until attrs.length) {
          val attr = attrs.item(i) as org.w3c.dom.Attr
          val name = attr.name
          if (name.startsWith("xmlns:")) {
            declarations.add(attr to name.substring(6))
          } else {
            extractPrefix(name)?.let { usedPrefixes.add(it) }
          }
        }
      }
      val children = node.childNodes
      for (i in 0 until children.length) {
        visit(children.item(i))
      }
    }

    visit(root)

    for ((attr, prefix) in declarations) {
      if (!usedPrefixes.contains(prefix)) {
        context.report(
          ISSUE,
          attr,
          context.getLocation(attr),
          "Unused namespace `$prefix`"
        )
      }
    }
  }

  private fun extractPrefix(name: String): String? {
    val index = name.indexOf(':')
    return if (index != -1) name.substring(0, index) else null
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "UnusedNamespace",
      briefDescription = "Unused namespace",
      explanation = "Unused namespace declarations take up space and require " +
        "processing that is not necessary",
      category = Category.PERFORMANCE,
      priority = 3,
      severity = Severity.WARNING,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}