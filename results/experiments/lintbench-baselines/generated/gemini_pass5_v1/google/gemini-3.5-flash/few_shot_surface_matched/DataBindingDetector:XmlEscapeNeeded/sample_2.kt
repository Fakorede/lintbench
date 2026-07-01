package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class DataBindingDetector : LayoutDetector(), XmlScanner {

    override fun getApplicableAttributes(): Collection<String> {
        return XmlScanner.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!value.startsWith("@{") && !value.startsWith("@={")) {
            return
        }

        val start = context.parser.getValueStartOffset(context, attribute)
        val end = context.parser.getValueEndOffset(context, attribute)
        if (start < 0 || end < 0) return

        val contents = context.getContents() ?: return
        if (start > end || end > contents.length) return

        val rawValue = contents.substring(start, end)

        var hasUnescaped = false
        for (i in rawValue.indices) {
            val char = rawValue[i]
            if (char == '<') {
                hasUnescaped = true
                break
            }
            if (char == '&' && !isEscaped(rawValue, i)) {
                hasUnescaped = true
                break
            }
        }

        if (hasUnescaped) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Missing XML Escape: When a string contains characters that have special usage in XML, you must escape the characters."
            )
        }
    }

    private fun isEscaped(text: String, index: Int): Boolean {
        if (text[index] != '&') return true
        val remainder = text.substring(index + 1)
        val entities = listOf("lt;", "gt;", "amp;", "quot;", "apos;")
        for (entity in entities) {
            if (remainder.startsWith(entity)) return true
        }
        if (remainder.startsWith("#")) {
            val semi = remainder.indexOf(';')
            if (semi in 2..8) {
                val numPart = remainder.substring(1, semi)
                if (numPart.startsWith("x") || numPart.startsWith("X")) {
                    if (numPart.substring(1).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                        return true
                    }
                } else if (numPart.all { it.isDigit() }) {
                    return true
                }
            }
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
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILE_SCOPE
            )
        )
    }
}