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
    val contents = context.getContents() ?: return
    if (location.start == null || location.end == null) return

    val rawValue = contents.subSequence(location.start.offset, location.end.offset)
    if (needsXmlEscape(rawValue)) {
      context.report(
        ISSUE,
        attribute,
        location,
        "The attribute value contains characters that must be escaped in XML"
      )
    }
  }

  private fun needsXmlEscape(raw: CharSequence): Boolean {
    for (i in raw.indices) {
      when (raw[i]) {
        '<' -> return true
        '&' -> if (!isEntityReference(raw, i)) return true
      }
    }
    return false
  }

  private fun isEntityReference(raw: CharSequence, start: Int): Boolean {
    var i = start + 1
    if (i >= raw.length) return false

    if (raw[i] == '#') {
      i++
      if (i < raw.length && (raw[i] == 'x' || raw[i] == 'X')) {
        i++
        if (i >= raw.length || !raw[i].isHexDigit()) return false
        i++
        while (i < raw.length && raw[i].isHexDigit()) i++
      } else {
        if (i >= raw.length || !raw[i].isDigit()) return false
        i++
        while (i < raw.length && raw[i].isDigit()) i++
      }
      return i < raw.length && raw[i] == ';'
    }

    if (!(raw[i].isLetter() || raw[i] == '_')) return false
    i++
    while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] in ".-_:")) i++
    return i < raw.length && raw[i] == ';'
  }

  private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "XmlEscapeNeeded",
      briefDescription = "Missing XML Escape",
      explanation = "When a string contains characters that have special usage in XML, " +
        "you must escape the characters (for example, use \"&lt;\" instead of \"<\").",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}