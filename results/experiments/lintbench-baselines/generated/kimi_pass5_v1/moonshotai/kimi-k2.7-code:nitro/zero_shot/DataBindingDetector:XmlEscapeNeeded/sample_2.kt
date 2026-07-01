package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, you must escape the characters.
                """,
            moreInfo = "https://developer.android.com/topic/libraries/data-binding/index.html",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val DATA_BINDING_EXPRESSION = Regex("""@(=?)\{([^}]*)\}""")
        private val XML_ENTITY = Regex("""&(amp|lt|gt|quot|apos);""")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String>? = XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!value.startsWith("@{") && !value.startsWith("@={")) {
            return
        }

        val valueLocation = context.getValueLocation(attribute)
        val start = valueLocation.start?.offset ?: return
        val end = valueLocation.end?.offset ?: return
        val contents = context.getContents() ?: return
        val rawValue = contents.substring(start, end).trim('"', '\'')

        for (match in DATA_BINDING_EXPRESSION.findAll(rawValue)) {
            val expression = match.groupValues[2]
            if (hasUnescapedXmlSpecialChar(expression)) {
                context.report(
                    ISSUE,
                    attribute,
                    valueLocation,
                    "Missing XML escape in data binding expression"
                )
                return
            }
        }
    }

    private fun hasUnescapedXmlSpecialChar(expression: String): Boolean {
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            if (c == '&') {
                val entityMatch = XML_ENTITY.find(expression, i)
                if (entityMatch != null && entityMatch.range.first == i) {
                    i = entityMatch.range.last + 1
                    continue
                }
                return true
            }
            if (c == '<' || c == '>') {
                return true
            }
            i++
        }
        return false
    }
}