package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string contains characters that have special usage in XML, \
                you must escape the characters.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val VALID_NAMED_ENTITIES = setOf("amp", "lt", "gt", "apos", "quot")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val source = context.getContents() ?: return
        checkSourceForUnescapedChars(context, document, source)
    }

    private fun checkSourceForUnescapedChars(context: XmlContext, document: Document, source: String) {
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when (c) {
                '&' -> {
                    // Check if it's a valid entity reference
                    val semicolonIndex = source.indexOf(';', i + 1)
                    if (semicolonIndex == -1) {
                        // No semicolon found - unescaped &
                        reportIssueAtOffset(context, document, source, i, c)
                        i++
                        continue
                    }
                    val entity = source.substring(i + 1, semicolonIndex)
                    if (!isValidEntity(entity)) {
                        reportIssueAtOffset(context, document, source, i, c)
                    }
                    i = semicolonIndex + 1
                    continue
                }
                '<' -> {
                    // Check if it's a tag or CDATA section - if so, skip
                    if (!isInsideTagOrComment(source, i)) {
                        reportIssueAtOffset(context, document, source, i, c)
                    }
                }
                else -> { /* ok */ }
            }
            i++
        }
    }

    private fun isInsideTagOrComment(source: String, offset: Int): Boolean {
        // A '<' at this position - check if it starts a tag, comment, or CDATA
        if (offset >= source.length) return false
        // Look ahead to see if this is a valid XML construct
        val rest = source.substring(offset)
        return rest.startsWith("</") ||
               rest.startsWith("<?") ||
               rest.startsWith("<!--") ||
               rest.startsWith("<![CDATA[") ||
               (rest.length > 1 && rest[1].isLetter())
    }

    private fun isValidEntity(entity: String): Boolean {
        if (entity.isEmpty()) return false
        if (entity in VALID_NAMED_ENTITIES) return true
        if (entity.startsWith("#")) {
            val numPart = entity.substring(1)
            if (numPart.startsWith("x") || numPart.startsWith("X")) {
                val hexPart = numPart.substring(1)
                return hexPart.isNotEmpty() && hexPart.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            }
            return numPart.isNotEmpty() && numPart.all { it.isDigit() }
        }
        return false
    }

    private fun reportIssueAtOffset(context: XmlContext, document: Document, source: String, offset: Int, c: Char) {
        val location = Location.create(context.file, source, offset, offset + 1)
        context.report(
            ISSUE,
            document.documentElement,
            location,
            "The character `$c` must be escaped in XML: use `${getEscapeSequence(c)}`"
        )
    }

    private fun getEscapeSequence(c: Char): String {
        return when (c) {
            '<' -> "&lt;"
            '>' -> "&gt;"
            '&' -> "&amp;"
            '"' -> "&quot;"
            '\'' -> "&apos;"
            else -> "&#${c.code};"
        }
    }
}