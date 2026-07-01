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
    checkElement(context, root)
  }

  private fun checkElement(context: XmlContext, element: org.w3c.dom.Element) {
    val attributes = element.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
      if (!attr.name.startsWith("xmlns")) continue

      val value = attr.value
      if (value.startsWith(RES_NAMESPACE_PREFIX) &&
          value != AUTO_URI &&
          value != ANDROID_URI) {
        val location = context.getValueLocation(attr) ?: continue
        context.report(
          ISSUE,
          attr,
          location,
          "Hardcoded package namespace found (`$value`); use `$AUTO_URI` instead"
        )
      }
    }

    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        checkElement(context, child as org.w3c.dom.Element)
      }
      child = child.nextSibling
    }
  }

  companion object {
    private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val RES_NAMESPACE_PREFIX = "http://schemas.android.com/apk/res/"

    @JvmField
    val ISSUE = Issue.create(
      id = "ResAuto",
      briefDescription = "Hardcoded Package in Namespace",
      explanation = """
        In Gradle projects, the actual package used in the final APK can vary; for example, you can add a `.debug` package suffix in one version and not the other. Therefore, you should not hardcode the application package in the resource namespace; instead, use `http://schemas.android.com/apk/res-auto`, which will cause the tools to figure out the right namespace for the resource regardless of the actual package used during the build.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 9,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}