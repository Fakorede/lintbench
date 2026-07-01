package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val URI_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace $AUTO_URI instead of a URI which includes \
                the library project's own package. This will be used to automatically adjust \
                the namespace of the attributes when the library resources are merged into \
                the application project.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: org.w3c.dom.Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
            val name = attr.name
            if (name.startsWith("xmlns:")) {
                val uri = attr.value
                if (uri.startsWith(URI_PREFIX) && uri != ANDROID_URI) {
                    if (context.project.isLibrary) {
                        val fix = context.fix()
                            .replace()
                            .text(uri)
                            .with(AUTO_URI)
                            .build()

                        context.report(
                            ISSUE,
                            attr,
                            context.getLocation(attr),
                            "In libraries, should use `$AUTO_URI` instead of `$uri` to avoid conflicts",
                            fix
                        )
                    }
                }
            }
        }
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is org.w3c.dom.Element) {
                checkElement(context, child)
            }
        }
    }
}