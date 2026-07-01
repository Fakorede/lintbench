package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : LayoutDetector() {

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i) as Attr
            val name = attribute.nodeName
            if (name != null && name.startsWith("xmlns:")) {
                val value = attribute.nodeValue
                if (value != null &&
                    value.startsWith(SdkConstants.URI_PREFIX) &&
                    value != SdkConstants.AUTO_URI
                ) {
                    if (context.project.isLibrary) {
                        val fix = fix()
                            .replace()
                            .text(value)
                            .with(SdkConstants.AUTO_URI)
                            .name("Replace with ${SdkConstants.AUTO_URI}")
                            .build()

                        context.report(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "By-value namespaces are not allowed in libraries; use the res-auto namespace instead",
                            fix
                        )
                    }
                }
            }
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                checkElement(context, child)
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "References to custom resources in library projects should use the res-auto namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the layout \
                must use the special namespace http://schemas.android.com/apk/res-auto instead \
                of a URI which includes the library project's own package. This will be used \
                to automatically adjust the namespace of the attributes when the library resources \
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