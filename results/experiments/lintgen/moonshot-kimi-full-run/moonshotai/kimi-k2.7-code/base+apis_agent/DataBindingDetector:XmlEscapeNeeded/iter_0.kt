package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr

class DataBindingDetector : Detector(), XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val valueLocation = context.getValueLocation(attribute)
        val start = valueLocation.start?.offset ?: return
        val end = valueLocation.end?.offset ?: return
        val contents = context.client.readFile(context.file)
        val rawValue = contents.substring(start, end)

        val quote = when {
            rawValue.startsWith("\"") && rawValue.endsWith("\"") -> '"'
            rawValue.startsWith("'") && rawValue.endsWith("'") -> '\''
            start > 0 && (contents[start - 1] == '"' || contents[start - 1] == '\'') -> contents[start - 1]
            else -> null
        }

        val unquoted = if (quote != null && rawValue.length >= 2 &&
            ((rawValue.startsWith("\"") && rawValue.endsWith("\"")) ||
                    (rawValue.startsWith("'") && rawValue.endsWith("'")))
        ) {
            rawValue.substring(1, rawValue.length - 1)
        } else {
            rawValue
        }

        if (unquoted.hasUnescapedXmlSpecialChar(quote)) {
            context.report(
                ISSUE,
                attribute,
                valueLocation,
                "Missing XML escape: the attribute value contains characters that must be escaped in XML."
            )
        }
    }

    private fun String.hasUnescapedXmlSpecialChar(quote: Char?): Boolean {
        var i = 0
        while (i < length) {
            when (this[i]) {
                '<' -> return true
                '&' -> if (!isEntityReferenceAt(i)) return true
                '>' -> return true
                else -> if (quote != null && this[i] == quote) return true
            }
            i++
        }
        return false
    }

    private fun String.isEntityReferenceAt(start: Int): Boolean {
        if (start + 1 >= length) return false
        val end = indexOf(';', start + 1)
        if (end == -1 || end - start > 10) return false
        val ref = substring(start + 1, end)
        return ref == "lt" || ref == "gt" || ref == "amp" || ref == "quot" || ref == "apos" ||
                ref.matches(Regex("#\\d+")) ||
                ref.matches(Regex("#x[0-9a-fA-F]+"))
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, you must escape the characters.
                Use XML entities such as &lt;, &gt;, &amp;, &quot;, and &apos;.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}