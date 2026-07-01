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
import org.w3c.dom.Document
import org.w3c.dom.Element

class NamespaceDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return true
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        checkElement(context, root)
    }

    private fun checkElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val attribute = attributes.item(i) as? Attr ?: continue
                val name = attribute.name ?: continue
                if (name.startsWith("xmlns:")) {
                    val value = attribute.value ?: continue
                    if (value == "http://schemas.android.com/apk/res/auto") {
                        val fix = fix()
                            .replace()
                            .text(value)
                            .with("http://schemas.android.com/apk/res-auto")
                            .autoFix()
                            .build()

                        context.report(
                            ISSUE,
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
                                ISSUE,
                                attribute,
                                context.getValueLocation(attribute),
                                "In Gradle projects, use the URI `http://schemas.android.com/apk/res-auto` for custom namespaces",
                                fix
                            )
                        }
                    }
                }
            }
        }
        val childNodes = element.childNodes
        if (childNodes != null) {
            for (i in 0 until childNodes.length) {
                val child = childNodes.item(i)
                if (child is Element) {
                    checkElement(context, child)
                }
            }
        }
    }

    companion object {
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
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                NamespaceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}