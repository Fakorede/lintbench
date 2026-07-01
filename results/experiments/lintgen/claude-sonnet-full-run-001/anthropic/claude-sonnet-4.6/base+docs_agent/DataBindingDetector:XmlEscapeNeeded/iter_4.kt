package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class DataBindingDetector : Detector(), XmlScanner {

    companion object {
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

        private val VALID_ENTITIES = setOf("amp", "lt", "gt", "apos", "quot")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableElements(): Collection<String>? {
        return XmlScannerConstants.ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Check text content of the element by examining child text nodes
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                val text = child.nodeValue
                if (text != null) {
                    checkForUnescapedChars(context, element, child, text)
                }
            }
            child = child.nextSibling
        }

        // Check attributes
        val attributes = element.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i) as? Attr ?: continue
                val value = attr.value ?: continue
                checkForUnescapedCharsInAttribute(context, attr, value)
            }
        }
    }

    private fun checkForUnescapedChars(context: XmlContext, element: Element, textNode: Node, text: String) {
        val rawText = getRawTextContent(context, textNode) ?: text

        var i = 0
        var found = false
        var foundChar = ' '
        while (i < rawText.length && !found) {
            val c = rawText[i]
            when (c) {
                '<' -> {
                    found = true
                    foundChar = c
                }
                '&' -> {
                    val semicolonIndex = rawText.indexOf(';', i)
                    if (semicolonIndex == -1) {
                        found = true
                        foundChar = c
                    } else {
                        val entity = rawText.substring(i + 1, semicolonIndex)
                        if (!isValidEntity(entity)) {
                            found = true
                            foundChar = c
                        } else {
                            i = semicolonIndex
                        }
                    }
                }
            }
            i++
        }
        if (found) {
            reportUnescaped(context, element, foundChar)
        }
    }

    private fun getRawTextContent(context: XmlContext, node: Node): String? {
        return try {
            val location = context.getLocation(node)
            val start = location.start ?: return null
            val end = location.end ?: return null
            val source = context.getContents() ?: return null
            val startOffset = start.offset
            val endOffset = end.offset
            if (startOffset >= 0 && endOffset > startOffset && endOffset <= source.length) {
                source.substring(startOffset, endOffset)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun checkForUnescapedCharsInAttribute(context: XmlContext, attribute: Attr, value: String) {
        val rawValue = getRawAttributeValue(context, attribute) ?: value

        var i = 0
        var found = false
        var foundChar = ' '
        while (i < rawValue.length && !found) {
            val c = rawValue[i]
            when (c) {
                '<' -> {
                    found = true
                    foundChar = c
                }
                '&' -> {
                    val semicolonIndex = rawValue.indexOf(';', i)
                    if (semicolonIndex == -1) {
                        found = true
                        foundChar = c
                    } else {
                        val entity = rawValue.substring(i + 1, semicolonIndex)
                        if (!isValidEntity(entity)) {
                            found = true
                            foundChar = c
                        } else {
                            i = semicolonIndex
                        }
                    }
                }
            }
            i++
        }
        if (found) {
            reportUnescapedAttr(context, attribute, foundChar)
        }
    }

    private fun getRawAttributeValue(context: XmlContext, attr: Attr): String? {
        return try {
            val location = context.getLocation(attr)
            val start = location.start ?: return null
            val end = location.end ?: return null
            val source = context.getContents() ?: return null
            val startOffset = start.offset
            val endOffset = end.offset
            if (startOffset >= 0 && endOffset > startOffset && endOffset <= source.length) {
                val attrText = source.substring(startOffset, endOffset)
                // Extract value between quotes
                val eqIdx = attrText.indexOf('=')
                if (eqIdx >= 0 && eqIdx + 1 < attrText.length) {
                    val afterEq = attrText.substring(eqIdx + 1).trim()
                    if (afterEq.length >= 2 && (afterEq[0] == '"' || afterEq[0] == '\'')) {
                        afterEq.substring(1, afterEq.length - 1)
                    } else afterEq
                } else attrText
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isValidEntity(entity: String): Boolean {
        if (entity.isEmpty()) return false
        if (VALID_ENTITIES.contains(entity)) {
            return true
        }
        if (entity.startsWith("#")) {
            val rest = entity.substring(1)
            if (rest.startsWith("x") || rest.startsWith("X")) {
                val hex = rest.substring(1)
                return hex.isNotEmpty() && hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            }
            return rest.isNotEmpty() && rest.all { it.isDigit() }
        }
        return false
    }

    private fun reportUnescaped(context: XmlContext, element: Element, c: Char) {
        val escapeSequence = when (c) {
            '<' -> "&lt;"
            '>' -> "&gt;"
            '&' -> "&amp;"
            '"' -> "&quot;"
            '\'' -> "&apos;"
            else -> "\\$c"
        }
        context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "The character '$c' must be escaped as `$escapeSequence` in XML"
        )
    }

    private fun reportUnescapedAttr(context: XmlContext, attribute: Attr, c: Char) {
        val escapeSequence = when (c) {
            '<' -> "&lt;"
            '>' -> "&gt;"
            '&' -> "&amp;"
            '"' -> "&quot;"
            '\'' -> "&apos;"
            else -> "\\$c"
        }
        context.report(
            ISSUE,
            attribute.ownerElement,
            context.getLocation(attribute),
            "The character '$c' must be escaped as `$escapeSequence` in XML"
        )
    }
}