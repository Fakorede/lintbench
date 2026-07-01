package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

class NamespaceDetector : ResourceXmlDetector() {

    companion object {
        private const val ANDROID_RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        @JvmField
        val ISSUE = Issue.create(
            id = "ResAuto",
            briefDescription = "Hardcoded Package in Namespace",
            explanation = """
                In Gradle projects, the actual package used in the final APK can vary; for \
                example, you can add a `.debug` package suffix in one version and not the other. \
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

    override fun getApplicableAttributes(): Collection<String>? = listOf("*")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (!attribute.nodeName.startsWith("xmlns:")) {
            return
        }

        val value = attribute.nodeValue ?: return
        if (value.startsWith(ANDROID_RES_PREFIX) && value != RES_AUTO && value != ANDROID_NS) {
            val fix = fix()
                .replace()
                .text(value)
                .with(RES_AUTO)
                .build()

            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "Hardcoded package in namespace URI",
                fix
            )
        }
    }
}