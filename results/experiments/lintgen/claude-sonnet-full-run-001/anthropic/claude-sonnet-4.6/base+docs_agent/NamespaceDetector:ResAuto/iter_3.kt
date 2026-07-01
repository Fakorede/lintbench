package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.URI_PREFIX
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
import org.w3c.dom.Element
import org.w3c.dom.Node

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
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

        // Wrong res-auto URL patterns that users sometimes use
        private val WRONG_RES_AUTO_URLS = listOf(
            "http://schemas.android.com/apk/res-auto/",
            "http://schemas.android.com/apk/res/auto"
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes = element.attributes ?: return
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            val name = attr.name ?: continue
            val value = attr.value ?: continue

            if (!name.startsWith("xmlns:")) continue

            // Check for hardcoded package namespaces like
            // http://schemas.android.com/apk/res/com.example.app
            if (value.startsWith(URI_PREFIX) &&
                value != ANDROID_URI &&
                value != AUTO_URI
            ) {
                context.report(
                    RES_AUTO,
                    attr,
                    context.getValueLocation(attr),
                    "Avoid hardcoding the namespace; use `$AUTO_URI` instead"
                )
                continue
            }

            // Check for wrong res-auto URL variants
            for (wrongUrl in WRONG_RES_AUTO_URLS) {
                if (value == wrongUrl) {
                    context.report(
                        RES_AUTO,
                        attr,
                        context.getValueLocation(attr),
                        "Avoid hardcoding the namespace; use `$AUTO_URI` instead"
                    )
                    break
                }
            }
        }

        // Recurse into child elements
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                checkElement(context, child as Element)
            }
        }
    }
}