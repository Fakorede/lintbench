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

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value ?: return
        val trimmed = value.trim()
        if (!((trimmed.startsWith("@{") || trimmed.startsWith("@={")) && trimmed.endsWith("}"))) {
            return
        }

        val contents = context.getContents() ?: return
        val location = context.getValueLocation(attribute)
        val start = location.start?.offset ?: -1
        val end = location.end?.offset ?: -1
        if (start < 0 || end > contents.length || start >= end) {
            return
        }

        val rawValue = contents.subSequence(start, end).toString()
        if (hasUnescapedXmlCharacters(rawValue)) {
            context.report(
                ISSUE,
                attribute,
                location,
                "When a string contains characters that have special usage in XML, you must escape the characters"
            )
        }
    }

    private fun hasUnescapedXmlCharacters(rawText: String): Boolean {
        var i = 0
        val len = rawText.length
        while (i < len) {
            val c = rawText[i]
            if (c == '<') {
                return true
            }
            if (c == '&') {
                if (!isValidEntity(rawText, i)) {
                    return true
                }
            }
            i++
        }
        return false
    }

    private fun isValidEntity(text: String, index: Int): Boolean {
        val end = text.indexOf(';', index)
        if (end == -1 || end - index > 10) {
            return false
        }
        val entity = text.substring(index + 1, end)
        if (entity == "lt" || entity == "gt" || entity == "amp" || entity == "quot" || entity == "apos") {
            return true
        }
        if (entity.startsWith("#")) {
            val num = entity.substring(1)
            if (num.startsWith("x") || num.startsWith("X")) {
                val hex = num.substring(1)
                return hex.isNotEmpty() && hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            }
            return num.isNotEmpty() && num.all { it.isDigit() }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(DataBindingDetector::class.java, Scope.LAYOUT_SCOPE)
        )
    }
}