package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"

        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for example, you can add a `.debug` package suffix in one version and not the other. Therefore, you should **not** hardcode the application package in the resource; instead, use the special namespace `http://schemas.android.com/apk/res-auto` which will cause the tools to figure out the right namespace for the resource regardless of the actual package used during the build.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val name = attr.nodeName
            if (name.startsWith("xmlns:")) {
                val value = attr.nodeValue
                if (value.startsWith(RES_PREFIX) &&
                    !value.endsWith("/auto") &&
                    !value.endsWith("/android")) {
                    context.report(
                        issue = ISSUE,
                        location = context.getLocation(attr),
                        message = "Should use `$RES_AUTO` instead of hardcoded package namespace",
                        quickfixData = fix()
                            .replace()
                            .text(value)
                            .with(RES_AUTO)
                            .autoFix()
                            .build()
                    )
                }
            }
        }
    }
}