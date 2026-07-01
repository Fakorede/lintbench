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

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val RES_AUTO: Issue = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; \
                for example, you can add a `.debug` package suffix in one version and \
                not the other. Therefore, you should **not** hardcode the application \
                package in the resource; instead, use the special namespace \
                `http://schemas.android.com/apk/res-auto` which will cause the tools \
                to figure out the right namespace for the resource regardless of the \
                actual package used during the build.
                """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private const val XMLNS_PREFIX = "xmlns:"
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return

        val attributes = root.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            if (!name.startsWith(XMLNS_PREFIX)) {
                continue
            }

            val value = attr.value ?: continue

            // Skip the standard Android namespace
            if (value == ANDROID_URI) {
                continue
            }

            // Skip the res-auto namespace (that's what we want people to use)
            if (value == AUTO_URI) {
                continue
            }

            // Check if this looks like a hardcoded res/ namespace
            // e.g. http://schemas.android.com/apk/res/com.example.app
            if (value.startsWith(URI_PREFIX)) {
                // URI_PREFIX is "http://schemas.android.com/apk/res/"
                // If there's something after the prefix, it's a hardcoded package
                val packagePart = value.substring(URI_PREFIX.length)
                if (packagePart.isNotEmpty()) {
                    context.report(
                        issue = RES_AUTO,
                        location = context.getLocation(attr),
                        message = "In Gradle projects, always use `$AUTO_URI` for custom " +
                                "attributes; replace `$value` with `$AUTO_URI`"
                    )
                }
            }
        }
    }
}