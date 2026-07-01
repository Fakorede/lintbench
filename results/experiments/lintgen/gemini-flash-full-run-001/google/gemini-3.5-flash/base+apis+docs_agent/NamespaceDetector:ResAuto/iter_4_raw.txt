package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class NamespaceDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val name = attribute.name ?: return
        if (name.startsWith("xmlns:")) {
            val value = attribute.value ?: return
            if (value == "http://schemas.android.com/apk/res/auto") {
                val fix = fix()
                    .replace()
                    .text(value)
                    .with("http://schemas.android.com/apk/res-auto")
                    .autoFix()
                    .build()

                context.report(
                    RES_AUTO,
                    attribute,
                    context.getValueLocation(attribute),
                    "Suspicious namespace: should be http://schemas.android.com/apk/res-auto",
                    fix
                )
            } else if (value.startsWith("http://schemas.android.com/apk/res/") &&
                value != "http://schemas.android.com/apk/res/android"
            ) {
                if (context.project.isGradleProject) {
                    val fix = fix()
                        .replace()
                        .text(value)
                        .with("http://schemas.android.com/apk/res-auto")
                        .autoFix()
                        .build()

                    context.report(
                        RES_AUTO,
                        attribute,
                        context.getValueLocation(attribute),
                        "In Gradle projects, use the URI `http://schemas.android.com/apk/res-auto` for custom namespaces",
                        fix
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val RES_AUTO = Issue.create(
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
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        @JvmField
        val ISSUE = RES_AUTO
    }
}