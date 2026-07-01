package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val PACKAGE_PREFIX = "http://schemas.android.com/apk/res/"

        private val IMPLEMENTATION = Implementation(
            NamespaceDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary;
                for example, you can add a `.debug` package suffix in one build type and
                not in another. When you hardcode the application package as a resource
                XML namespace (e.g. http://schemas.android.com/apk/res/com.example.app),
                the resource may not be found if the final package differs. Use
                http://schemas.android.com/apk/res-auto instead so the tools resolve the
                correct package automatically.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
        checkElement(context, document.documentElement)
    }

    private fun checkElement(context: XmlContext, element: org.w3c.dom.Element?) {
        if (element == null) {
            return
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.nodeName
            if (name == "xmlns" || name.startsWith("xmlns:")) {
                val uri = attr.nodeValue ?: continue
                if (uri.startsWith(PACKAGE_PREFIX) && uri != ANDROID_URI && uri != AUTO_URI) {
                    val message = "Hardcoded package namespace `$uri`; should use `$AUTO_URI`"
                    context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        message,
                    )
                }
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                checkElement(context, child as org.w3c.dom.Element)
            }
            child = child.nextSibling
        }
    }
}