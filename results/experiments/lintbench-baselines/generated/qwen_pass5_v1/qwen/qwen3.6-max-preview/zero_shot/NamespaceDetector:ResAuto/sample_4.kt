package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr

class NamespaceDetector : ResourceXmlDetector() {

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val name = attribute.name
        if (!name.startsWith("xmlns:") && name != "xmlns") return

        val value = attribute.value
        if (value.startsWith("http://schemas.android.com/apk/") &&
            value != "http://schemas.android.com/apk/res/android" &&
            value != "http://schemas.android.com/apk/res-auto") {

            val fix = LintFix.create()
                .name("Replace with res-auto")
                .replace()
                .text(value)
                .with("http://schemas.android.com/apk/res-auto")
                .autoFix()
                .build()

            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Hardcoded package name in namespace; use `res-auto` instead",
                fix
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for  example,you can add a `.debug` package suffix in one version and not the other.  Therefore, you should **not** hardcode the application package in the resource;  instead, use the special namespace `http://schemas.android.com/apk/res-auto`  which will cause the tools to figure out the right namespace for the resource  regardless of the actual package used during the build.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(NamespaceDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}