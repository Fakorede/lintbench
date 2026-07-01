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
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap

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

        private const val APP_RES_AUTO = AUTO_URI
        private const val HTTP_PREFIX = "http://schemas.android.com/apk/res/"
        private const val RES_AUTO_SUFFIX = "res-auto"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        // Only check library projects
        val project: Project = context.project
        if (!project.isLibrary) {
            return
        }

        val root: Element = document.documentElement ?: return

        val attributes: NamedNodeMap = root.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr: Attr = attributes.item(i) as? Attr ?: continue
            val name: String = attr.name ?: continue
            if (!name.startsWith(XMLNS_PREFIX)) {
                continue
            }

            val uri: String = attr.value ?: continue

            // Skip the standard Android namespace and tools namespace
            if (uri == "http://schemas.android.com/apk/res/android" ||
                uri == TOOLS_URI ||
                uri == "http://schemas.android.com/apk/res-auto" ||
                uri == APP_RES_AUTO
            ) {
                continue
            }

            // Check if this is a custom app namespace (http://schemas.android.com/apk/res/<package>)
            if (uri.startsWith(HTTP_PREFIX) && !uri.endsWith(RES_AUTO_SUFFIX)) {
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "When using a custom namespace attribute in a library project, " +
                        "use the namespace `\"$APP_RES_AUTO\"` instead."
                )
            }
        }
    }
}