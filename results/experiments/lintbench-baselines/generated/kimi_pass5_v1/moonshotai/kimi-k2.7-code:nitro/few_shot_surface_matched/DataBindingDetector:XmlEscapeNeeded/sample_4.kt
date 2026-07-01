package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class DataBindingDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableAttributes(): Collection<String> {
    return XmlScanner.ALL
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    val location = context.getValueLocation(attribute)
    val start = location.start?.offset ?: return
    val end = location.end?.offset ?: return
    if (end <= start) return

    val contents = context.getContents() ?: return
    val rawValue = contents.subSequence(start, end).toString()

    if (rawValue.needsXmlEscape()) {
      context.report(
        ISSUE,
        attribute,
        location,
        "This attribute value contains characters that must be escaped in XML; " +
          "for example, use `&lt;` for `<` and `&amp;` for `&`."
      )
    }
  }

  private fun String.needsXmlEscape(): Boolean {
    if (contains('<')) return true

    var index = indexOf('&')
    while (index >= 0) {
      val semi = indexOf(';', index + 1)
      if (semi == -1) return true

      val name = substring(index + 1, semi)
      val isEntity = name.matches(Regex("[A-Za-z][A-Za-z0-9]*")) ||
        name.matches(Regex("#[0-9]+")) ||
        name.matches(Regex("#x[0-9a-fA-F]+"))

      if (!isEntity) return true

      index = indexOf('&', index + 1)
    }

    return false
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "XmlEscapeNeeded",
      briefDescription = "Missing XML escape",
      explanation = "When a string contains characters that have special usage in XML, " +
        "you must escape the characters. For example, use `&lt;` for `<` and `&amp;` for `&`.",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}