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
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE
        )

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
            implementation = IMPLEMENTATION
        )

        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val APP_RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val LIB_RES_PREFIX = "http://schemas.android.com/apk/lib/"
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

            val uri = attr.value ?: continue

            // Skip the standard Android namespace and tools namespace and auto namespace
            if (uri == ANDROID_URI || uri == TOOLS_URI || uri == AUTO_URI) {
                continue
            }

            // Check if this is an app-specific resource namespace (not the standard android one)
            if (uri.startsWith(APP_RES_PREFIX) || uri.startsWith(LIB_RES_PREFIX)) {
                // This is a custom namespace that should be res-auto
                context.report(
                    CUSTOM_VIEW,
                    attr,
                    context.getValueLocation(attr),
                    "In a library project, the namespace for custom attributes should be `$AUTO_URI` instead of a URI based on the library's package name"
                )
            }
        }
    }
}