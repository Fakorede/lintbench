package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.XMLNS_PREFIX
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

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
        private const val ANDROID_PKG_PREFIX = "http://schemas.android.com/apk/res/"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "LibraryCustomView",
            briefDescription = "Custom views in libraries should use res-auto-namespace",
            explanation = """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace `$AUTO_URI` instead of a URI which \
                includes the library project's own package. This will be used to automatically \
                adjust the namespace of the attributes when the library resources are merged \
                into the application project.
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
        if (!context.project.isLibrary) {
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
            val value = attr.value ?: continue
            // Skip the standard android namespace and tools namespace
            if (value == TOOLS_URI) {
                continue
            }
            // Check if this is a res/ URI that is NOT res-auto
            if (value.startsWith(ANDROID_PKG_PREFIX) && value != AUTO_URI) {
                // This is a package-specific namespace URI in a library — should use res-auto
                val fix = fix()
                    .name("Replace with res-auto namespace")
                    .replace()
                    .text(value)
                    .with(AUTO_URI)
                    .build()

                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "When using a custom view with custom attributes in a library project, " +
                        "the layout should use the special namespace `$AUTO_URI` instead of " +
                        "a URI which includes the library project's own package.",
                    fix
                )
            }
        }
    }
}