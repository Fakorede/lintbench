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
    val declared = mutableMapOf<String, org.w3c.dom.Attr>()

    val rootAttributes = root.attributes
    for (i in 0 until rootAttributes.length) {
      val attr = rootAttributes.item(i) as? org.w3c.dom.Attr ?: continue
      val name = attr.name
      if (name.startsWith("xmlns:")) {
        val prefix = name.substring("xmlns:".length)
        if (prefix.isNotEmpty()) {
          declared[prefix] = attr
        }
      }
    }

    if (declared.isEmpty()) return

    val used = mutableSetOf<String>()
    val allElements = document.getElementsByTagName("*")
    for (i in 0 until allElements.length) {
      val element = allElements.item(i) as? org.w3c.dom.Element ?: continue

      val tagName = element.tagName
      val tagColon = tagName.indexOf(':')
      if (tagColon > 0) {
        used.add(tagName.substring(0, tagColon))
      }

      val attributes = element.attributes
      for (j in 0 until attributes.length) {
        val attr = attributes.item(j) as? org.w3c.dom.Attr ?: continue
        val attrName = attr.name
        if (attrName.startsWith("xmlns:")) continue
        val attrColon = attrName.indexOf(':')
        if (attrColon > 0) {
          used.add(attrName.substring(0, attrColon))
        }
      }
    }

    for ((prefix, attr) in declared) {
      if (prefix !in used) {
        context.report(
          ISSUE,
          attr,
          context.getLocation(attr),
          "Unused namespace `$prefix`"
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
      priority = 2,
      severity = Severity.WARNING,
      implementation = Implementation(
        NamespaceDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}