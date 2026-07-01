package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document

class NamespaceDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace `$AUTO_URI` instead of a URI which includes \
                the library project's own package. This will be used to automatically adjust \
                the namespace of the attributes when the library resources are merged into \
                the application project.
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

    override fun visitDocument(context: XmlContext, document: Document) {
        // Only check library projects
        val project: Project = context.project
        if (!project.isLibrary) {
            return
        }

        val root = document.documentElement ?: return

        val attributes = root.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            if (!name.startsWith(XMLNS_PREFIX)) {
                continue
            }
            val uri = attr.value ?: continue

            // Skip well-known URIs that are fine
            if (uri == AUTO_URI || uri == TOOLS_URI) {
                continue
            }

            // Check if this looks like an app namespace URI (http://schemas.android.com/apk/res/...)
            // but is not the auto URI
            if (uri.startsWith("http://schemas.android.com/apk/res/") &&
                !uri.endsWith("/android")
            ) {
                context.report(
                    ISSUE,
                    attr,
                    context.getLocation(attr),
                    "In a library project, the layout file should use the special " +
                        "namespace `$AUTO_URI` instead of a URI which includes " +
                        "the library project's own package"
                )
            }
        }
    }
}