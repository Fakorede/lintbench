package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val XMLNS_PREFIX = "xmlns:"
        private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
        private const val APKLIB_PREFIX = "http://schemas.android.com/apk/res/"
        private const val APKLIB_PACKAGE_PREFIX = "http://schemas.android.com/apk/res-auto"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; \
                for example, you can add a `.debug` package suffix in one version and \
                not the other. Therefore, you should **not** hardcode the application \
                package in the resource; instead, use the special namespace \
                `http://schemas.android.com/apk/res-auto` which will cause the tools \
                to figure out the right namespace for the resource regardless of the \
                actual package used during the build.
                """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes: NamedNodeMap = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if (attr.nodeType == Node.ATTRIBUTE_NODE) {
                val name = attr.nodeName ?: continue
                if (name.startsWith(XMLNS_PREFIX)) {
                    val value = attr.nodeValue ?: continue
                    if (value.startsWith(APKLIB_PREFIX) && value != RES_AUTO) {
                        context.report(
                            issue = ISSUE,
                            location = context.getLocation(attr),
                            message = "Hardcoded package `${value}` in namespace; use `$RES_AUTO` instead",
                        )
                    }
                }
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element)
            }
            child = child.nextSibling
        }
    }
}