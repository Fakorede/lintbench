package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; \
                for example, you can add a `.debug` package suffix in one version and not the other. \
                Therefore, you should not hardcode the application package in the resource; \
                instead, use the special namespace `http://schemas.android.com/apk/res-auto` \
                which will cause the tools to figure out the right namespace for the resource \
                regardless of the actual package used during the build.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        if (!context.project.isGradleProject) {
            return
        }
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: org.w3c.dom.Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
            val name = attr.nodeName
            if (name == "xmlns" || name.startsWith("xmlns:")) {
                val uri = attr.nodeValue
                if (uri != null &&
                    uri.startsWith("http://schemas.android.com/apk/res/") &&
                    uri != "http://schemas.android.com/apk/res/android"
                ) {
                    val fix = context.fix()
                        .replace()
                        .with("http://schemas.android.com/apk/res-auto")
                        .build()
                    context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "Avoid using hardcoded package in namespace; use `http://schemas.android.com/apk/res-auto` instead",
                        fix
                    )
                }
            }
        }
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child is org.w3c.dom.Element) {
                checkElement(context, child)
            }
        }
    }
}