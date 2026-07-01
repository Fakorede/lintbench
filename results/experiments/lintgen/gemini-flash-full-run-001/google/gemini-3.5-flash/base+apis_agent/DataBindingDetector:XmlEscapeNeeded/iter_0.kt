package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Document

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "XmlEscapeNeeded",
            briefDescription = "Missing XML Escape",
            explanation = """
                When a string or expression contains characters that have special usage in XML \
                (such as `<`, `&`, or quotes matching the attribute delimiter), you must escape \
                the characters using XML entities (e.g. `&lt;`, `&amp;`, `&quot;`, or `&apos;`).
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                DataBindingDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val ENTITY_REGEX = Regex("^&(lt|gt|amp|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val contents = context.getContents()?.toString() ?: return
        var index = 0
        while (index < contents.length) {
            val start = contents.indexOf("@{", index)
            val twoWayStart = contents.indexOf("@={", index)
            val exprStart = if (start != -1 && twoWayStart != -1) {
                minOf(start, twoWayStart)
            } else if (start != -1) {
                start
            } else {
                twoWayStart
            }

            if (exprStart == -1) break

            val exprEnd = findMatchingBrace(contents, exprStart)
            if (exprEnd == -1) {
                index = exprStart + 2
                continue
            }

            var quoteChar = ' '
            for (i in exprStart - 1 downTo 0) {
                val c = contents[i]
                if (c == '"' || c == '\'') {
                    quoteChar = c
                    break
                }
                if (c == '=') {
                    break
                }
            }

            var i = exprStart
            while (i <= exprEnd) {
                val c = contents[i]
                if (c == '<') {
                    context.report(
                        ISSUE,
                        Location.create(context.file, contents, i, i + 1),
                        "Must escape `<` as `&lt;` in XML attributes"
                    )
                } else if (c == '&') {
                    val remaining = contents.substring(i)
                    val match = ENTITY_REGEX.find(remaining)
                    if (match != null && match.range.start == 0) {
                        i += match.value.length - 1
                    } else {
                        context.report(
                            ISSUE,
                            Location.create(context.file, contents, i, i + 1),
                            "Must escape `&` as `&amp;` in XML attributes"
                        )
                    }
                } else if (quoteChar != ' ' && c == quoteChar) {
                    val startSel = if (i > 0 && contents[i - 1] == '\\') i - 1 else i
                    val endSel = i + 1
                    val msg = if (quoteChar == '"') {
                        "Character `\"` must be escaped as `&quot;` or use single quotes `'` for the attribute"
                    } else {
                        "Character `\'` must be escaped as `&apos;` or use double quotes `\"` for the attribute"
                    }
                    context.report(
                        ISSUE,
                        Location.create(context.file, contents, startSel, endSel),
                        msg
                    )
                }
                i++
            }

            index = exprEnd + 1
        }
    }

    private fun findMatchingBrace(text: String, start: Int): Int {
        var depth = 0
        var inDoubleQuote = false
        var inSingleQuote = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            if (inDoubleQuote) {
                if (c == '\\' && i + 1 < text.length) {
                    i += 2
                    continue
                }
                if (c == '"') {
                    inDoubleQuote = false
                }
            } else if (inSingleQuote) {
                if (c == '\\' && i + 1 < text.length) {
                    i += 2
                    continue
                }
                if (c == '\'') {
                    inSingleQuote = false
                }
            } else {
                if (c == '"') {
                    inDoubleQuote = true
                } else if (c == '\'') {
                    inSingleQuote = true
                } else if (c == '{') {
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        return i
                    }
                }
            }
            i++
        }
        return -1
    }
}