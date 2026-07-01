package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
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
    val elements = document.getElementsByTagName("*")
    for (i in 0 until elements.length) {
      val element = elements.item(i) as? Element ?: continue
      val attributes = element.attributes
      for (j in 0 until attributes.length) {
        val attr = attributes.item(j) as? Attr ?: continue
        if (attr.name == XMLNS || attr.name.startsWith(XMLNS_PREFIX)) {
          val value = attr.value
          if (value.startsWith(RESOURCE_NAMESPACE_PREFIX) &&
              value != ANDROID_URI &&
              value != AUTO_URI) {
            context.report(
              ISSUE,
              attr,
              context.getValueLocation(attr),
              "Hardcoded package namespace in resource; use `$AUTO_URI` instead"
            )
          }
        }
      }
    }
  }

  companion object {
    private const val RESOURCE_NAMESPACE_PREFIX = "http://schemas.android.com/apk/res/"
    private const val XMLNS = "xmlns"
    private const val XMLNS_PREFIX = "xmlns:"

    @JvmField
    val ISSUE = Issue.create(
      id = "ResAuto",
      briefDescription = "Hardcoded Package Namespace in Resource",
      explanation = "In Gradle projects, the actual package used in the final APK can vary; " +
        "for example, you can add a `.debug` package suffix in one version and not the other. " +
        "Therefore, you should not hardcode the application package in the resource; " +
        "instead, use the special namespace `$AUTO_URI` which will cause the tools to " +
        "figure out the right namespace for the resource regardless of the actual package " +
        "used during the build.",
      category = Category.CORRECTNESS,
      priority = 9,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}