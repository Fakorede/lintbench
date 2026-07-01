package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
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
            explanation = "When a string contains characters that have special usage in XML, you must escape the characters.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val location = context.getValueLocation(attribute)
        val contents = context.contents
        if (location.start >= location.end || location.end > contents.length) return

        val rawValue = contents.substring(location.start, location.end)
        val stripQuotes = rawValue.length >= 2 &&
            ((rawValue[0] == '"' && rawValue.last() == '"') ||
             (rawValue[0] == '\'' && rawValue.last() == '\''))

        val valueContent = if (stripQuotes) rawValue.substring(1, rawValue.length - 1) else rawValue
        val quoteOffset = if (stripQuotes) 1 else 0

        // Only inspect data binding expressions
        if (!valueContent.contains("@{") && !valueContent.contains("@{")) return

        var i = 0
        while (i < valueContent.length) {
            val c = valueContent[i]
            if (c == '<') {
                val start = location.start + quoteOffset + i
                val charLoc = Location.create(context.file, contents, start, start + 1)
                context.report(ISSUE, attribute, charLoc, "Replace `<` with `&lt;`")
            } else if (c == '&') {
                val remaining = valueContent.substring(i)
                if (!isValidXmlEntity(remaining)) {
                    val start = location.start + quoteOffset + i
                    val charLoc = Location.create(context.file, contents, start, start + 1)
                    context.report(ISSUE, attribute, charLoc, "Replace `&` with `&amp;`")
                }
            }
            i++
        }
    }

    private fun isValidXmlEntity(s: String): Boolean {
        val semi = s.indexOf(';')
        if (semi == -1) return false
        val entity = s.substring(0, semi + 1)
        return entity == "&amp;" || entity == "&lt;" || entity == "&gt;" ||
               entity == "&quot;" || entity == "&apos;" || entity.startsWith("&#")
    }
}