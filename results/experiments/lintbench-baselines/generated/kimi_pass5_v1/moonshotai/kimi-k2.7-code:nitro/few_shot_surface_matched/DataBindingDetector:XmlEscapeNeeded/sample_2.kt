package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class DataBindingDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableAttributes(): Collection<String>? {
    return Detector.XmlScannerConstants.ALL
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    val location = context.getValueLocation(attribute)
    val startOffset = location.start?.offset ?: return
    val endOffset = location.end?.offset ?: return
    val contents = context.getContents() ?: return
    if (endOffset > contents.length) return

    val rawValue = contents.subSequence(startOffset, endOffset).toString()
    if (needsXmlEscape(rawValue)) {
      context.report(
        ISSUE,
        attribute,
        location,
        "The attribute value contains characters that have special meaning in XML. " +
          "Escape them (for example, use &lt;, &gt;, and &amp;) or the value may be parsed incorrectly."
      )
    }
  }

  private fun needsXmlEscape(rawValue: String): Boolean {
    var i = 0
    while (i < rawValue.length) {
      when (rawValue[i]) {
        '<', '>' -> return true
        '&' -> {
          val semi = rawValue.indexOf(';', i + 1)
          if (semi == -1) return true
          val entity = rawValue.substring(i + 1, semi)
          if (!isValidEntityReference(entity)) return true
          i = semi
        }
      }
      i++
    }
    return false
  }

  private fun isValidEntityReference(entity: String): Boolean {
    if (entity.isEmpty()) return false
    return when {
      entity[0] == '#' -> {
        val reference = entity.substring(1)
        reference.isNotEmpty() &&
          (reference.matches(Regex("\\d+")) || reference.matches(Regex("[Xx][0-9A-Fa-f]+")))
      }
      else -> entity.matches(Regex("[A-Za-z][A-Za-z0-9]*"))
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "XmlEscapeNeeded",
      briefDescription = "Missing XML Escape",
      explanation = "When a string contains characters that have special usage in XML, " +
        "you must escape the characters using the corresponding XML entity (e.g., &lt;, &gt;, &amp;).",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.ERROR,
      implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}