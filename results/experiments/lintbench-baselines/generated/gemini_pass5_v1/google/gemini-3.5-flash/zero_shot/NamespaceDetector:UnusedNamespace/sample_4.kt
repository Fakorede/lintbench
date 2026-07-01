package com.android.tools.lint.checks

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
import org.w3c.dom.Node
import java.util.EnumSet

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableFiles(): EnumSet<Scope> =
        EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)

    override fun visitDocument(context: XmlContext, document: Document) {
        val declaredPrefixes = mutableMapOf<String, Attr>()
        val usedPrefixes = mutableSetOf<String>()

        fun visit(node: Node) {
            if (node is Element) {
                val elemName = node.nodeName
                val elemPrefix = node.prefix ?: if (elemName.contains(':')) elemName.substringBefore(':') else null
                if (elemPrefix != null) {
                    usedPrefixes.add(elemPrefix)
                }

                val attributes = node.attributes
                for (i in 0 until attributes.length) {
                    val attr = attributes.item(i) as Attr
                    val name = attr.nodeName
                    if (name.startsWith("xmlns:")) {
                        val prefix = name.substring(6)
                        declaredPrefixes[prefix] = attr
                    } else {
                        val attrPrefix = attr.prefix ?: if (name.contains(':')) name.substringBefore(':') else null
                        if (attrPrefix != null) {
                            usedPrefixes.add(attrPrefix)
                        }
                    }
                }

                val children = node.childNodes
                for (i in 0 until children.length) {
                    visit(children.item(i))
                }
            }
        }

        visit(document)

        for ((prefix, attr) in declaredPrefixes) {
            if (prefix !in usedPrefixes) {
                val location = context.getNameLocation(attr)
                val fix = fix()
                    .replace()
                    .range(context.getLocation(attr))
                    .with("")
                    .reformat(true)
                    .build()

                context.report(
                    ISSUE,
                    attr,
                    location,
                    "Unused namespace `$prefix`",
                    fix
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "UnusedNamespace",
            briefDescription = "Unused namespace",
            explanation = "Unused namespace declarations take up space and require processing that is not necessary",
            category = Category.PERFORMANCE,
            priority = 1,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)
            )
        )
    }
}