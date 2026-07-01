package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document

class NamespaceDetector : ResourceXmlDetector() {

  override fun visitDocument(context: XmlContext, document: Document) {
    if (!context.project.isLibrary) return

    val root = document.documentElement ?: return
    val attributes = root.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i)
      if (attr.nodeName.startsWith("xmlns:")) {
        val value = attr.nodeValue
        if (value.startsWith("http://schemas.android.com/apk/") && value != AUTO_URI) {
          context.report(
            ISSUE,
            attr,
            context.getValueLocation(attr),
            "Custom view in library project should use `$AUTO_URI` namespace instead of explicit package namespace"
          )
        }
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "LibraryCustomView",
      briefDescription = "Custom views in libraries should use res-auto-namespace",
      explanation = "When using a custom view with custom attributes in a library project, " +
        "the layout must use the special namespace `$AUTO_URI` instead of a URI which includes " +
        "the library project's own package. This will be used to automatically adjust " +
        "the namespace of the attributes when the library resources are merged into " +
        "the application project.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}