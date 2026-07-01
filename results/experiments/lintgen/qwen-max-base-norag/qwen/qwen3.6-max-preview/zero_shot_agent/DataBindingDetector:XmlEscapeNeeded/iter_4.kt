package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        private val NUMERIC_ENTITY_REGEX = Regex("&#\\d+;")
        private val HEX_ENTITY_REGEX = Regex("&#x[0-9a-fA-F]+;")

        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val contents = try {
            context.file.readText().replace("\r\n", "\n").replace("\r", "\n")
        } catch (e: Exception) {
            return
        }

        val loc = context.getLocation(attribute)
        val start = loc.start?.offset ?: return
        val end = loc.end?.offset ?: return
        if (start < 0 || end > contents.length || start >= end) return

        val rawAttr = contents.substring(start, end)
        val eqIndex = rawAttr.indexOf('=')
        if (eqIndex == -1) return

        val valuePart = rawAttr.substring(eqIndex + 1).trim()
        if (valuePart.length < 2) return

        val quote = valuePart[0]
        if ((quote == '"' || quote == '\'') && valuePart.last() == quote) {
            val inner = valuePart.substring(1, valuePart.length - 1)
            if (hasUnescapedXmlChars(inner)) {
                context.report(
                    ISSUE,
                    loc,
                    "Missing XML Escape: Attribute value contains unescaped '<' or '&'"
                )
            }
        }
    }

    private fun hasUnescapedXmlChars(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '<') return true
            if (c == '&') {
                val semiIndex = s.indexOf(';', i + 1)
                if (semiIndex == -1 || semiIndex > i + 10) return true
                val entity = s.substring(i, semiIndex + 1)
                if (!isValidEntity(entity)) return true
                i = semiIndex + 1
            } else {
                i++
            }
        }
        return false
    }

    private fun isValidEntity(entity: String): Boolean {
        return entity == "&lt;" || entity == "&gt;" || entity == "&amp;" ||
               entity == "&quot;" || entity == "&apos;" ||
               entity.matches(NUMERIC_ENTITY_REGEX) || entity.matches(HEX_ENTITY_REGEX)
    }
}