package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class NamespaceDetector : ResourceXmlDetector() {

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.name
            if (name.startsWith("xmlns:") || name == "xmlns") {
                val value = attr.value
                if (value.startsWith(ANDROID_RES_PREFIX) &&
                    value != "http://schemas.android.com/apk/res/android" &&
                    value != "http://schemas.android.com/apk/res-auto") {
                    context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Should use `http://schemas.android.com/apk/res-auto` instead of hardcoded package namespace",
                        fix().replace().text(value).with("http://schemas.android.com/apk/res-auto").build()
                    )
                }
            }
        }
    }

    companion object {
        private const val ANDROID_RES_PREFIX = "http://schemas.android.com/apk/res/"

        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; \
                for example, you can add a `.debug` package suffix in one version and not the other. \
                Therefore, you should **not** hardcode the application package in the resource; \
                instead, use the special namespace `http://schemas.android.com/apk/res-auto` \
                which will cause the tools to figure out the right namespace for the resource \
                regardless of the actual package used during the build.
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
}