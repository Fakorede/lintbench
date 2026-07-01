package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector.XmlScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class DataBindingDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableAttributes(): Collection<String> = XmlScanner.ALL

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    val contents = context.getContents() ?: return
    val location = context.getValueLocation(attribute)
    val start = location.start?.offset ?: return
    val end = location.end?.offset ?: return
    if (start < 0 || end > contents.length || start >= end) return

    val rawValue = contents.subSequence(start, end).toString()
    if (rawValue.contains('<') || rawValue.contains(UNESCAPED_AMPERSAND)) {
      context.report(
        ISSUE,
        attribute,
        location,
        "The string contains characters that have special usage in XML; " +
          "you must escape the characters."
      )
    }
  }

  companion object {
    private val UNESCAPED_AMPERSAND =
      Regex("&(?!(?:amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);)")

    @JvmField
    val ISSUE = Issue.create(
      id = "XmlEscapeNeeded",
      briefDescription = "Missing XML escape",
      explanation = "When a string contains characters that have special usage in XML, " +
        "you must escape the characters.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
    )
  }
}