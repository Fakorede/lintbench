package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.URI_PREFIX
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : LayoutDetector() {

    companion object {
        @JvmField
        val CUSTOM_VIEW = Issue.create(
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

        val root: Element = document.documentElement ?: return

        val attributes = root.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            if (!name.startsWith(XMLNS_PREFIX)) {
                continue
            }

            val value = attr.value ?: continue

            // Skip standard namespaces
            if (value == AUTO_URI || value == TOOLS_URI) {
                continue
            }

            // Check if this is a custom namespace (starts with URI_PREFIX / res/
            // i.e. http://schemas.android.com/apk/res/ but NOT the standard Android namespace)
            if (value.startsWith(URI_PREFIX)) {
                // This is a package-specific namespace like http://schemas.android.com/apk/res/com.example.foo
                // In a library project, this should be AUTO_URI instead
                val fix = LintFix.create()
                    .name("Replace with res-auto namespace")
                    .set()
                    .attribute(name)
                    .value(AUTO_URI)
                    .build()

                context.report(
                    CUSTOM_VIEW,
                    attr,
                    context.getValueLocation(attr),
                    "When using a custom view with custom attributes in a library project, " +
                        "use the special namespace `$AUTO_URI` instead of a URI which includes " +
                        "the library project's own package",
                    fix
                )
            }
        }
    }
}