package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class NamespaceDetector : ResourceXmlDetector(), XmlScanner {

  override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
    if (!context.project.isLibrary) {
      return
    }

    val androidUri = com.android.SdkConstants.ANDROID_URI
    val autoUri = com.android.SdkConstants.AUTO_URI
    val resUriPrefix = "http://schemas.android.com/apk/res/"
    val elements = document.getElementsByTagName("*")

    for (i in 0 until elements.length) {
      val element = elements.item(i) as? org.w3c.dom.Element ?: continue
      if (!element.tagName.contains('.')) {
        continue
      }

      val attributes = element.attributes
      for (j in 0 until attributes.length) {
        val attr = attributes.item(j) as? org.w3c.dom.Attr ?: continue
        val name = attr.name
        if (name.startsWith("xmlns")) {
          continue
        }

        val uri = attr.namespaceURI
        if (!uri.isNullOrEmpty() &&
            uri != androidUri &&
            uri != autoUri &&
            uri.startsWith(resUriPrefix)) {
          val message = "When using a custom view with custom attributes in a library project, " +
              "the layout must use the namespace $autoUri instead of a URI " +
              "which includes the library project's own package."
          context.report(ISSUE, attr, context.getLocation(attr), message)
        }
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "LibraryCustomView",
      briefDescription = "Library custom views should use res-auto-namespace",
      explanation = "When using a custom view with custom attributes in a library project, " +
          "the layout must use the special namespace ${com.android.SdkConstants.AUTO_URI} " +
          "instead of a URI which includes the library project's own package. This will be used " +
          "to automatically adjust the namespace of the attributes when the library resources " +
          "are merged into the application project.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
    )
  }
}