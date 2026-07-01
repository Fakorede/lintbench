package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr

class DataBindingDetector : LayoutDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            DataBindingDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters. For example, in data binding expressions, you must escape `<` as `&lt;` and `&` as `&amp;`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: return
        val end = location.end?.offset ?: return
        val contents = context.getContents() ?: return
        if (start < 0 || end > contents.length || start > end) return
        val rawValue = contents.substring(start, end)

        val trimmed = rawValue.trim()
        val isDataBinding = trimmed.startsWith("@{") || trimmed.startsWith("@={")
        if (!isDataBinding) return

        if (hasUnescapedCharacters(rawValue)) {
            context.report(
                issue = ISSUE,
                scope = attribute,
                location = location,
                message = "Characters like `<` and `&` must be escaped in XML (e.g. `&lt;` and `&amp;`)"
            )
        }
    }

    private fun hasUnescapedCharacters(rawValue: String): Boolean {
        var i = 0
        val n = rawValue.length
        while (i < n) {
            val c = rawValue[i]
            if (c == '<') {
                return true
            }
            if (c == '&') {
                if (!isValidEntity(rawValue, i)) {
                    return true
                }
            }
            i++
        }
        return false
    }

    private fun isValidEntity(s: String, ampersandIndex: Int): Boolean {
        val semiIndex = s.indexOf(';', ampersandIndex + 1)
        if (semiIndex == -1) return false
        val entity = s.substring(ampersandIndex + 1, semiIndex)
        if (entity == "amp" || entity == "lt" || entity == "gt" || entity == "apos" || entity == "quot") {
            return true
        }
        if (entity.startsWith("#")) {
            val num = entity.substring(1)
            if (num.startsWith("x") || num.startsWith("X")) {
                val hex = num.substring(1)
                return hex.isNotEmpty() && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            } else {
                return num.isNotEmpty() && num.all { it in '0'..'9' }
            }
        }
        return false
    }
}