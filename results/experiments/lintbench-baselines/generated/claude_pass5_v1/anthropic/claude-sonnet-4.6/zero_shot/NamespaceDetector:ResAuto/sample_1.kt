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
 * Detector for hardcoded package namespaces in XML resource files.
 * Checks that `http://schemas.android.com/apk/res/<package>` is not used
 * and suggests `http://schemas.android.com/apk/res-auto` instead.
 */
class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        /** The main issue this detector reports */
        @JvmField
        val RES_AUTO: Issue = Issue.create(
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
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root: Element = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes: NamedNodeMap = element.attributes ?: return

        for (i in 0 until attributes.length) {
            val attr: Attr = attributes.item(i) as? Attr ?: continue
            checkAttribute(context, attr)
        }

        // Recurse into children
        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element)
            }
            child = child.nextSibling
        }
    }

    private fun checkAttribute(context: XmlContext, attr: Attr) {
        val name = attr.name ?: return
        if (!name.startsWith(XMLNS_PREFIX) && name != XMLNS) {
            return
        }

        val uri = attr.value ?: return

        // Skip the standard Android namespace
        if (uri == ANDROID_URI) {
            return
        }

        // Skip the res-auto namespace (that's what we want people to use)
        if (uri == AUTO_URI) {
            return
        }

        // Check if this is a hardcoded apk/res/<package> namespace
        if (uri.startsWith(URI_PREFIX)) {
            // URI_PREFIX is "http://schemas.android.com/apk/res/"
            // So anything that starts with this prefix (and isn't res-auto) is hardcoded
            val fix = fix()
                .name("Replace with res-auto")
                .replace()
                .text(uri)
                .with(AUTO_URI)
                .build()

            context.report(
                issue = RES_AUTO,
                location = context.getValueLocation(attr),
                message = "Avoid hardcoding the namespace; use `res-auto` instead",
                quickfixData = fix
            )
        }
    }
}