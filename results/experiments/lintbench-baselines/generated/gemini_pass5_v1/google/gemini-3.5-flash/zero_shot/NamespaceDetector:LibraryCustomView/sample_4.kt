package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!context.project.isLibrary) {
            return
        }
        val nodeList = document.getElementsByTagName("*")
        for (i in 0 until nodeList.length) {
            val element = nodeList.item(i) as Element
            val attributes = element.attributes
            for (j in 0 until attributes.length) {
                val attribute = attributes.item(j) as Attr
                val name = attribute.name
                if (name.startsWith("xmlns:")) {
                    val value = attribute.value
                    if (value.startsWith("http://schemas.android.com/apk/res/") &&
                        value != "http://schemas.android.com/apk/res/android" &&
                        value != "http://schemas.android.com/apk/res-auto") {
                        
                        val fix = fix().replace().text(value).with("http://schemas.android.com/apk/res-auto").build()
                        context.report(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "In library projects, custom attributes should specify `http://schemas.android.com/apk/res-auto` instead of the local package to avoid merging issues",
                            fix
                        )
                    }
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace http://schemas.android.com/apk/res-auto instead of a \
                URI which includes the library project's own package. This will be used to \
                automatically adjust the namespace of the attributes when the library resources \
                are merged into the application project.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}