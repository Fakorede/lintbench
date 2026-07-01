package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.URI_PREFIX
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

/**
 * Checks for hardcoded package names in XML namespace declarations.
 */
class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        /** The main issue discovered by this detector */
        @JvmField
        val RES_AUTO = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; \
                for example, you can add a `.debug` package suffix in one version and not \
                the other. Therefore, you should **not** hardcode the application package \
                in the resource; instead, use the special namespace \
                `http://schemas.android.com/apk/res-auto` which will cause the tools to \
                figure out the right namespace for the resource regardless of the actual \
                package used during the build.
                """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val XMLNS_PREFIX = "xmlns:"
        private const val XMLNS = "xmlns"
        // URI_PREFIX is "http://schemas.android.com/apk/res/"
        // We also need to catch "http://schemas.android.com/apk/res-auto" variants
        // and wrong res-auto URLs like "http://schemas.android.com/apk/res-auto/"
        private const val SCHEMAS_ANDROID_APK = "http://schemas.android.com/apk/"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes: NamedNodeMap = element.attributes ?: return

        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue

            if (name.startsWith(XMLNS_PREFIX) || name == XMLNS) {
                val value = attr.value ?: continue
                checkNamespaceValue(context, attr, value)
            }
        }

        // Recurse into children
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element)
            }
            child = child.nextSibling
        }
    }

    private fun checkNamespaceValue(context: XmlContext, attr: Attr, value: String) {
        // Skip the standard Android namespace
        if (value == ANDROID_URI) {
            return
        }

        // Skip the correct res-auto namespace (that's what we want people to use)
        if (value == AUTO_URI) {
            return
        }

        // Check if this looks like a hardcoded app resource namespace
        // Pattern: http://schemas.android.com/apk/res/<package>
        // Also catch wrong res-auto URLs like http://schemas.android.com/apk/res-auto/
        if (value.startsWith(URI_PREFIX)) {
            // It starts with the res prefix but is not res-auto
            // This means it has a hardcoded package name
            val message = "Avoid hardcoding the namespace; use " +
                    "`http://schemas.android.com/apk/res-auto` instead"

            context.report(
                issue = RES_AUTO,
                location = context.getValueLocation(attr),
                message = message
            )
        } else if (value.startsWith(SCHEMAS_ANDROID_APK) && !value.startsWith(AUTO_URI)) {
            // Catch other variants under the apk/ path that aren't the correct AUTO_URI
            // e.g. http://schemas.android.com/apk/res-auto/ (with trailing slash)
            val message = "Avoid hardcoding the namespace; use " +
                    "`http://schemas.android.com/apk/res-auto` instead"

            context.report(
                issue = RES_AUTO,
                location = context.getValueLocation(attr),
                message = message
            )
        }
    }
}