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

  override fun visitDocument(context: XmlContext) {
    val document = context.document
    val elements = document.getElementsByTagName("*")
    val prefix = "http://schemas.android.com/apk/res/"
    val auto = "http://schemas.android.com/apk/res-auto"
    val android = "http://schemas.android.com/apk/res/android"

    for (i in 0 until elements.length) {
      val element = elements.item(i) ?: continue
      val attributes = element.attributes ?: continue
      for (j in 0 until attributes.length) {
        val attr = attributes.item(j) ?: continue
        val name = attr.nodeName ?: continue
        val value = attr.nodeValue ?: continue
        if (name.startsWith("xmlns") &&
            value.startsWith(prefix) &&
            value != auto &&
            value != android) {
          context.report(
            ISSUE,
            attr,
            context.getLocation(attr),
            "Hardcoded package namespace in resource file; use " +
              "http://schemas.android.com/apk/res-auto instead"
          )
        }
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ResAuto",
      briefDescription = "Hardcoded Package in Namespace",
      explanation = "In Gradle projects, the actual package used in the final APK can vary. " +
        "Do not hardcode the application package in resources; use the " +
        "http://schemas.android.com/apk/res-auto namespace so the tools determine " +
        "the correct package regardless of the actual package used during the build.",
      category = Category.CORRECTNESS,
      priority = 9,
      severity = Severity.FATAL,
      implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}