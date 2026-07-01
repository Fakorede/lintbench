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

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

  override fun visitDocument(context: XmlContext, document: Document) {
    if (!context.project.isLibrary) return
    val packageName = context.project.packageName ?: return
    val targetUri = "http://schemas.android.com/apk/res/$packageName"

    val root = document.documentElement ?: return
    val attributes = root.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i)
      if (attr.nodeValue == targetUri) {
        context.report(
          ISSUE,
          attr,
          context.getValueLocation(attr as Attr),
          "Custom views in libraries should use res-auto-namespace instead of the library's own package"
        )
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "LibraryCustomView",
      briefDescription = "Custom views in libraries should use res-auto-namespace",
      explanation = "When using a custom view with custom attributes in a library project, the " +
        "layout must use the special namespace http://schemas.android.com/apk/res-auto instead " +
        "of a URI which includes the library project's own package. This will be used to " +
        "automatically adjust the namespace of the attributes when the library resources are " +
        "merged into the application project.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}