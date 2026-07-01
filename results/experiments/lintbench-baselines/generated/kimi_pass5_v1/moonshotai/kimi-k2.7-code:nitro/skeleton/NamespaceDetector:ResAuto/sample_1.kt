package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val RESOURCE_URI_PREFIX = "http://schemas.android.com/apk/res/"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects the package used in the final APK can vary, for example with
                build type or product flavor suffixes such as `.debug`. Therefore you should not
                hardcode the application package in resource XML namespaces. Instead use
                `$AUTO_URI`, which will cause the tools to resolve the correct package
                automatically.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION
        )
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        document.documentElement?.let { checkNode(it, context) }
    }

    private fun checkNode(node: org.w3c.dom.Node, context: XmlContext) {
        val attributes = node.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val attr = attributes.item(i)
                val name = attr.nodeName
                if (name == "xmlns" || name.startsWith("xmlns:")) {
                    val value = attr.nodeValue ?: continue
                    if (value.startsWith(RESOURCE_URI_PREFIX) &&
                        value != AUTO_URI &&
                        value != ANDROID_URI
                    ) {
                        report(context, attr)
                    }
                }
                val nsUri = attr.namespaceUri
                if (nsUri != null &&
                    nsUri.startsWith(RESOURCE_URI_PREFIX) &&
                    nsUri != AUTO_URI &&
                    nsUri != ANDROID_URI
                ) {
                    report(context, attr)
                }
            }
        }

        val children = node.childNodes
        if (children != null) {
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    checkNode(child, context)
                }
            }
        }
    }

    private fun report(context: XmlContext, attr: org.w3c.dom.Node) {
        val location = context.getLocation(attr)
        val message = "Hardcoded package namespace in resources should be replaced with `$AUTO_URI`"
        context.report(ISSUE, location, message)
    }
}