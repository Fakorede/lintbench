package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? = XmlScanner.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!isNamespaceDeclaration(attribute)) {
            return
        }
        if (value.startsWith(PACKAGE_NS_PREFIX) &&
            value.length > PACKAGE_NS_PREFIX.length &&
            value != SdkConstants.ANDROID_URI
        ) {
            val fix = LintFix.create()
                .replace()
                .text(value)
                .with(SdkConstants.AUTO_URI)
                .build()

            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Hardcoded package namespace found; use ${SdkConstants.AUTO_URI} instead",
                fix
            )
        }
    }

    private fun isNamespaceDeclaration(attribute: Attr): Boolean {
        val name = attribute.name
        return name == SdkConstants.XMLNS || name.startsWith(SdkConstants.XMLNS_PREFIX)
    }

    companion object {
        private const val PACKAGE_NS_PREFIX = "http://schemas.android.com/apk/res/"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for example,
                you can add a `.debug` package suffix in one version and not the other. Therefore, you
                should not hardcode the application package in the resource; instead, use the special
                namespace `${SdkConstants.AUTO_URI}` which will cause the tools to figure out the right
                namespace for the resource regardless of the actual package used during the build.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}