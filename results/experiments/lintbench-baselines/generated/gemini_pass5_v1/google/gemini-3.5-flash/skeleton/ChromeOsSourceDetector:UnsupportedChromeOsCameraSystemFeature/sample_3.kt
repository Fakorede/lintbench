package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ChromeOsSourceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
                Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen \
                devices don't have, as well as newer device configurations and modes may place the device \
                in a state where the rear camera is not available. To include all possible cameras that \
                may be on the device, look for `FEATURE_CAMERA_ANY` instead.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("hasSystemFeature")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            return
        }

        val args = node.valueArguments
        if (args.size == 1) {
            val argument = args[0]
            val value = argument.evaluate() as? String
            if (value == "android.hardware.camera") {
                val source = argument.asSourceString()
                val replacement = if (source.contains("FEATURE_CAMERA")) {
                    source.replace("FEATURE_CAMERA", "FEATURE_CAMERA_ANY")
                } else {
                    "\"android.hardware.camera.any\""
                }

                val fix = fix()
                    .name("Replace with FEATURE_CAMERA_ANY")
                    .replace()
                    .with(replacement)
                    .reformat(true)
                    .build()

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(argument),
                    "Look for `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support devices without a rear camera",
                    fix
                )
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = null

    override fun createUastHandler(context: JavaContext): UElementHandler? = null
}