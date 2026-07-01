package com.android.tools.lint.checks

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
            val attribute = attributes.item(i) as? Attr ?: continue
            val name = attribute.name
            if (name.startsWith("xmlns:")) {
                val uri = attribute.value
                if (uri.startsWith("http://schemas.android.com/apk/res/") &&
                    uri != "http://schemas.android.com/apk/res/android"
                ) {
                    if (context.project.isLibrary) {
                        val fix = fix()
                            .replace()
                            .text(uri)
                            .with("http://schemas.android.com/apk/res-auto")
                            .build()

                        context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "When using a custom view with custom attributes in a " +
                                    "library project, the layout must use the special " +
                                    "namespace http://schemas.android.com/apk/res-auto " +
                                    "instead of a URI which includes the library project's " +
                                    "own package",
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
            briefDescription = "References to custom views in libraries must use the res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace http://schemas.android.com/apk/res-auto \
                instead of a URI which includes the library project's own package. This will be \
                used to automatically adjust the namespace of the attributes when the library \
                resources are merged into the application project.
                """,
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