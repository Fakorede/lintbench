package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector

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
                In Gradle projects, the actual package used in the final APK can vary; for \
                example, you can add a `.debug` package suffix in one version and not the other. \
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

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: org.w3c.dom.Element) {
        val attributes = element.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val attribute = attributes.item(i) as? org.w3c.dom.Attr ?: continue
                val name = attribute.nodeName ?: continue
                if (name.startsWith("xmlns:")) {
                    val value = attribute.nodeValue ?: continue
                    if (value.startsWith("http://schemas.android.com/apk/res/") &&
                        value != "http://schemas.android.com/apk/res/android" &&
                        context.project.isGradleProject
                    ) {
                        val fix = context.fix()
                            .name("Replace with http://schemas.android.com/apk/res-auto")
                            .replace()
                            .text(value)
                            .with("http://schemas.android.com/apk/res-auto")
                            .build()

                        context.report(
                            ISSUE,
                            attribute,
                            context.getValueLocation(attribute),
                            "In Gradle projects, always use `http://schemas.android.com/apk/res-auto` for custom attributes",
                            fix
                        )
                    }
                }
            }
        }
        val childNodes = element.childNodes
        if (childNodes != null) {
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child != null && child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    checkElement(context, child as org.w3c.dom.Element)
                }
            }
        }
    }
}