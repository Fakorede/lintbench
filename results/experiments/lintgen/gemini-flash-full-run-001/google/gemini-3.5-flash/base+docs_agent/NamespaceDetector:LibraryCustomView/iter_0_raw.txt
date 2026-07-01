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
import java.util.EnumSet

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableFiles(): EnumSet<Scope> = Scope.RESOURCE_FILE_SCOPE

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!context.project.isLibrary) {
            return
        }
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i) as Attr
            val name = attribute.nodeName
            if (name.startsWith("xmlns:")) {
                val uri = attribute.nodeValue
                if (uri != null &&
                    uri.startsWith("http://schemas.android.com/apk/res/") &&
                    uri != "http://schemas.android.com/apk/res/android"
                ) {
                    val fix = fix()
                        .replace()
                        .with("http://schemas.android.com/apk/res-auto")
                        .build()

                    context.report(
                        ISSUE,
                        attribute,
                        context.getValueLocation(attribute),
                        "In libraries, should use `http://schemas.android.com/apk/res-auto` instead of `$uri` to avoid conflicts",
                        fix
                    )
                }
            }
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is Element) {
                checkElement(context, child)
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
                layout must use the special namespace http://schemas.android.com/apk/res-auto \
                instead of a URI which includes the library project's own package. This will \
                be used to automatically adjust the namespace of the attributes when the \
                library resources are merged into the application project.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}