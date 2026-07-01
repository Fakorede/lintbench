package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr

class NamespaceDetector : Detector(), XmlScanner {

    companion object {
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

        private const val RES_AUTO = "http://schemas.android.com/apk/res-auto"
        private const val RES_PREFIX = "http://schemas.android.com/apk/res/"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (!attribute.name.startsWith("xmlns:")) return

        val value = attribute.value
        if (value.startsWith(RES_PREFIX) && value != ANDROID_NS && value != RES_AUTO) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "Hardcoded package in namespace; use `$RES_AUTO` instead",
                fix()
                    .replace()
                    .name("Replace with res-auto")
                    .text(value)
                    .with(RES_AUTO)
                    .autoFix()
                    .build()
            )
        }
    }
}