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

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement ?: return
    checkNamespaces(context, root)
  }

  private fun checkNamespaces(context: XmlContext, element: Element) {
    val attributes = element.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) as Attr
      val name = attr.name
      if (name.startsWith("xmlns:") || name == "xmlns") {
        val value = attr.value
        if (value.startsWith("http://schemas.android.com/apk/res/") &&
            value != "http://schemas.android.com/apk/res/android" &&
            value != "http://schemas.android.com/apk/res-auto") {
          context.report(
            ISSUE,
            attr,
            context.getValueLocation(attr),
            "Should use `http://schemas.android.com/apk/res-auto` instead of hardcoded package namespace"
          )
        }
      }
    }

    var child = element.firstChild
    while (child != null) {
      if (child is Element) {
        checkNamespaces(context, child)
      }
      child = child.nextSibling
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ResAuto",
      briefDescription = "Hardcoded Package in Namespace",
      explanation = "In Gradle projects, the actual package used in the final APK can vary; " +
        "for example, you can add a `.debug` package suffix in one version and not the other. " +
        "Therefore, you should **not** hardcode the application package in the resource; " +
        "instead, use the special namespace `http://schemas.android.com/apk/res-auto` " +
        "which will cause the tools to figure out the right namespace for the resource " +
        "regardless of the actual package used during the build.",
      category = Category.CORRECTNESS,
      priority = 7,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}