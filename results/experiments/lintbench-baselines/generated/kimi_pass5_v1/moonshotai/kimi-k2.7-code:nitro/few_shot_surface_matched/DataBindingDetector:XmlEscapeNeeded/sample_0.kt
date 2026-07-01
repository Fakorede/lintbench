package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class DataBindingDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableAttributes(): Collection<String> {
    return XmlScannerConstants.ALL
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    if (attribute.value.isNullOrEmpty()) return

    val location = context.getValueLocation(attribute)
    val contents = context.getContents() ?: return
    val start = location.start?.offset ?: return
    val end = location.end?.offset ?: return
    if (start < 0 || end > contents.length || start >= end) return

    val rawValue = contents.subSequence(start, end).toString()
    if (rawValue.containsUnescapedXmlSpecialCharacter()) {
      context.report(
        ISSUE,
        attribute,
        location,
        "Missing XML escape: the attribute value contains characters that must be escaped in XML"
      )
    }
  }

  private fun String.containsUnescapedXmlSpecialCharacter(): Boolean {
    return UNESCAPED_XML_SPECIAL_REGEX.containsMatchIn(this)
  }

  companion object {
    private val UNESCAPED_XML_SPECIAL_REGEX = Regex(
      "[<>]|&(?!(?:#[0-9]+|#x[0-9a-fA-F]+|[_:a-zA-Z][\\w.:-]*);)"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "XmlEscapeNeeded",
      briefDescription = "Missing XML escape",
      explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}