package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.LintFix
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
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace http://schemas.android.com/apk/res-auto \
                instead of a URI which includes the library project's own package. This will \
                be used to automatically adjust the namespace of the attributes when the \
                library resources are merged into the application project.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!context.project.isLibrary) {
            return
        }
        val root = document.documentElement ?: return
        val attributes = root.attributes ?: return
        for (i in 0 until attributes.length) {
            val attribute = attributes.item(i) as? org.w3c.dom.Attr ?: continue
            val name = attribute.nodeName ?: continue
            if (name.startsWith("xmlns:")) {
                val uri = attribute.nodeValue ?: continue
                if (uri.startsWith("http://schemas.android.com/apk/res/") &&
                    uri != "http://schemas.android.com/apk/res/android" &&
                    uri != "http://schemas.android.com/apk/res-auto"
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
    }
}