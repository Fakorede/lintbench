package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.util.EnumSet
import javax.xml.XMLConstants

class NamespaceDetector : Detector(), Detector.XmlScanner {

    override fun visitDocument(context: XmlContext, document: Document) {
        val uses = mutableSetOf<Pair<String, String>>()
        val declarations = mutableListOf<Triple<Attr, String, String>>()

        fun recordUse(prefix: String, uri: String) {
            if (uri.isNotEmpty()) {
                uses.add(prefix to uri)
            }
        }

        fun processElement(element: Element) {
            recordUse(element.prefix ?: "", element.namespaceURI ?: "")

            for (i in 0 until element.attributes.length) {
                val attr = element.attributes.item(i) as Attr
                if (isNamespaceDeclaration(attr)) {
                    val declaredPrefix = if (attr.name == XMLConstants.XMLNS_ATTRIBUTE) {
                        ""
                    } else {
                        attr.localName ?: attr.name.substringAfter(":", "")
                    }
                    declarations.add(Triple(attr, declaredPrefix, attr.value ?: ""))
                } else {
                    recordUse(attr.prefix ?: "", attr.namespaceURI ?: "")
                }
            }

            for (i in 0 until element.childNodes.length) {
                val child = element.childNodes.item(i)
                if (child is Element) {
                    processElement(child)
                }
            }
        }

        processElement(document.documentElement)

        for ((attr, prefix, uri) in declarations) {
            if ((prefix to uri) !in uses) {
                context.report(
                    ISSUE,
                    context.getLocation(attr),
                    "Unused namespace declaration `${attr.name}`"
                )
            }
        }
    }

    private fun isNamespaceDeclaration(attr: Attr): Boolean {
        return attr.name == XMLConstants.XMLNS_ATTRIBUTE ||
            attr.name.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":") ||
            attr.namespaceURI == XMLConstants.XMLNS_ATTRIBUTE_NS_URI
    }

    companion object {
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace declaration",
            explanation = """
                Unused namespace declarations take up space and require processing that is not necessary.
                Consider removing unused namespace declarations.
            """,
            category = Category.PERFORMANCE,
            priority = 3,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                EnumSet.of(Scope.RESOURCE_XML_SCOPE, Scope.MANIFEST_SCOPE)
            )
        )
    }
}