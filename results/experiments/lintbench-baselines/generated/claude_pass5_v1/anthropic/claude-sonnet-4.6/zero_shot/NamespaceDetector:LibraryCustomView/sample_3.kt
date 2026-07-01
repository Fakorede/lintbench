package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.XMLNS_PREFIX
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

/**
 * Checks that custom views in library projects use the res-auto namespace
 * instead of a package-specific URI.
 */
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

        private const val APP_RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        // Only check library projects
        if (!context.project.isLibrary) {
            return
        }

        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        // Check namespace declarations on this element
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            if (!name.startsWith(XMLNS_PREFIX)) {
                continue
            }

            val uri = attr.value ?: continue

            // Skip the android namespace and tools namespace
            if (uri == ANDROID_URI || uri == TOOLS_URI || uri == AUTO_URI) {
                continue
            }

            // Check if this is an app res URI (http://schemas.android.com/apk/res/<package>)
            if (uri.startsWith(APP_RES_PREFIX)) {
                val packageName = uri.substring(APP_RES_PREFIX.length)
                // Make sure it's not the android package itself
                if (packageName != "android") {
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "In a library project, the `res-auto` namespace (`$AUTO_URI`) " +
                            "should be used instead of a URI which includes the library project's own package (`$uri`)"
                    )
                }
            }
        }

        // Recurse into children
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                checkElement(context, child)
            }
        }
    }
}