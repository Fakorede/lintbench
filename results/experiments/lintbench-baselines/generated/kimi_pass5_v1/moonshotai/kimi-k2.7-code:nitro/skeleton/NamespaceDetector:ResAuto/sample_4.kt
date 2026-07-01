package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
        private const val RESOURCE_SCHEMA_PREFIX = "http://schemas.android.com/apk/res/"
        private const val XMLNS_PREFIX = "xmlns"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = "In Gradle projects, the package used in the final APK can vary " +
                "(for example you can add a `.debug` package suffix in one build type and not in another). " +
                "For this reason, you should not hardcode the application package in XML resources. " +
                "Use `http://schemas.android.com/apk/res-auto` instead, which will automatically " +
                "figure out the correct namespace based on the actual package used during the build.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        visitElement(context, root)
    }

    private fun visitElement(context: XmlContext, element: Element) {
        val attributes: NamedNodeMap = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.name.startsWith(XMLNS_PREFIX)) {
                val uri = attr.value
                if (isHardcodedPackageNamespace(uri)) {
                    val message = "Hardcoded package namespace `$uri` should be replaced with `$RES_AUTO`"
                    val fix = LintFix.create()
                        .replace()
                        .text(uri)
                        .with(RES_AUTO)
                        .auto()
                        .build()
                    context.report(ISSUE, context.getValueLocation(attr), message, fix)
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                visitElement(context, child as Element)
            }
        }
    }

    private fun isHardcodedPackageNamespace(uri: String): Boolean {
        if (!uri.startsWith(RESOURCE_SCHEMA_PREFIX)) return false
        val suffix = uri.substring(RESOURCE_SCHEMA_PREFIX.length)
        return suffix.isNotEmpty() &&
            suffix != "android" &&
            suffix != "auto" &&
            suffix.contains('.')
    }
}