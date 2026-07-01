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
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = "When using a custom view with custom attributes in a library project, the layout must use the special namespace http://schemas.android.com/apk/res-auto instead of a URI which includes the library project's own package. This will be used to automatically adjust the namespace of the attributes when the library resources are merged into the application project.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!context.project.isLibrary) return

        val autoUri = "http://schemas.android.com/apk/res-auto"
        val resPrefix = "http://schemas.android.com/apk/res/"

        val elements = document.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val element = elements.item(i)
            val attributes = element.attributes ?: continue
            for (j in 0 until attributes.length) {
                val attr = attributes.item(j)
                val name = attr.nodeName
                val value = attr.nodeValue ?: continue
                if ((name == "xmlns" || name.startsWith("xmlns:")) &&
                    value.startsWith(resPrefix) && value != autoUri) {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Custom views in libraries should use res-auto-namespace instead of `$value`"
                    )
                }
            }
        }
    }
}