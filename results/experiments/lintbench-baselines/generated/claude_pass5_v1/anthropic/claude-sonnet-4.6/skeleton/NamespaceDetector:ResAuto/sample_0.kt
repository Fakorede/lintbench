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
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
        private const val APK_RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val APK_PKG_PREFIX = "http://schemas.android.com/apk/prv/res/"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val XMLNS_PREFIX = "xmlns:"

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
            val attr = attributes.item(i) ?: continue
            val name = attr.nodeName ?: continue
            if (name.startsWith(XMLNS_PREFIX)) {
                val value = attr.nodeValue ?: continue
                if (value == ANDROID_NS) {
                    // This is the standard android namespace — allowed
                    continue
                }
                if (value.startsWith(APK_RES_PREFIX) || value.startsWith(APK_PKG_PREFIX)) {
                    if (context.project.isGradleProject) {
                        context.report(
                            ISSUE,
                            attr,
                            context.getValueLocation(attr as org.w3c.dom.Attr),
                            "In Gradle projects, always use `$RES_AUTO` for custom " +
                                    "attributes; replace `$value` with `$RES_AUTO`",
                        )
                    }
                }
            }
        }

        // Recurse into children
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element)
            }
            child = child.nextSibling
        }
    }
}