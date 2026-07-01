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
import org.w3c.dom.Document
import org.w3c.dom.Element

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, you must escape them. \
                For example, in Data Binding expressions, use `&lt;` instead of `<`, `&amp;` instead of `&`, \
                and ensure nested quotes are properly escaped or use alternating single/double quotes.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val ENTITY_PATTERN = Regex("&[a-zA-Z0-9#x]+;")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!value.contains("@{") && !value.contains("@={")) {
            return
        }

        val location = context.getValueLocation(attribute)
        val startOffset = location.start?.offset ?: return
        val endOffset = location.end?.offset ?: return
        val contents = context.getContents() ?: return

        if (startOffset < 0 || endOffset > contents.length || startOffset >= endOffset) {
            return
        }

        val rawValue = contents.substring(startOffset, endOffset)

        // Determine the quote character used to wrap the attribute
        val quoteChar = if (startOffset > 0) {
            val charBefore = contents[startOffset - 1]
            if (charBefore == '"' || charBefore == '\'') charBefore else null
        } else {
            null
        }

        // Check for unescaped '<'
        if (rawValue.contains("<")) {
            context.report(
                ISSUE,
                location,
                "The `<` character must be escaped as `&lt;` in XML attributes"
            )
            return
        }

        // Check for unescaped '&'
        var index = rawValue.indexOf('&')
        while (index != -1) {
            val remainder = rawValue.substring(index)
            val match = ENTITY_PATTERN.find(remainder)
            if (match == null || match.range.first != 0) {
                context.report(
                    ISSUE,
                    location,
                    "The `&` character must be escaped as `&amp;` in XML attributes"
                )
                return
            }
            index = rawValue.indexOf('&', index + 1)
        }

        // Check for nested quote issues
        if (quoteChar != null) {
            if (quoteChar == '"' && rawValue.contains("\"")) {
                context.report(
                    ISSUE,
                    location,
                    "Quote character inside attribute enclosed in double quotes must be escaped as `&quot;` or use single quotes"
                )
                return
            }
            if (quoteChar == '\'' && rawValue.contains("'")) {
                context.report(
                    ISSUE,
                    location,
                    "Quote character inside attribute enclosed in single quotes must be escaped as `&apos;` or use double quotes"
                )
                return
            }
        }
    }
}