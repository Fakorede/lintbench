package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(type: ResourceType): Boolean = type == ResourceType.LAYOUT

  override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
    if (!context.project.isLibrary) {
      return
    }

    val root = document.documentElement ?: return
    val packageName = context.project.getPackage() ?: return
    val packageNamespace = "http://schemas.android.com/apk/res/$packageName"

    val attributes = root.attributes ?: return
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
      if (attr.prefix != "xmlns") {
        continue
      }
      val value = attr.value ?: continue
      if (value == packageNamespace || value.startsWith("$packageNamespace/")) {
        context.report(
          ISSUE,
          attr,
          context.getValueLocation(attr),
          "Use the res-auto namespace http://schemas.android.com/apk/res-auto " +
            "instead of a package-specific namespace for custom attributes in a library project"
        )
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "LibraryCustomView",
      briefDescription = "Custom views in libraries should use res-auto-namespace",
      explanation = "When using a custom view with custom attributes in a library project, " +
        "the layout must use the special namespace http://schemas.android.com/apk/res-auto " +
        "instead of a URI which includes the library project's own package. This will be used " +
        "to automatically adjust the namespace of the attributes when the library resources are " +
        "merged into the application project.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}