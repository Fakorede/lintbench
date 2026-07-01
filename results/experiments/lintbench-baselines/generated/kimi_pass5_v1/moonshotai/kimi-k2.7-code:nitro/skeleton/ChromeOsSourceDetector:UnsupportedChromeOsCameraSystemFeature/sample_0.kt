package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
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
                You should look for the `FEATURE_CAMERA_ANY` features to include all possible \
                cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks \
                for a rear facing camera, which certain large screen devices don't have, \
                as well as newer device configurations and modes may place the device in a \
                state where the rear camera is not available. To fix the issue, look for \
                `FEATURE_CAMERA_ANY` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UCallExpression::class.java)

    override fun getApplicableMethodNames(): List<String>? =
        listOf("hasSystemFeature")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!isPackageManagerHasSystemFeature(method)) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) {
            return
        }

        val firstArg = args[0]
        val feature = ConstantEvaluator.evaluate(context, firstArg) as? String ?: return
        if (feature != "android.hardware.camera") {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Use `PackageManager.FEATURE_CAMERA_ANY` instead of `PackageManager.FEATURE_CAMERA`; " +
                "the rear-facing camera may not be available on large screen devices or in all device modes."
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not used.
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                if (method.name != "hasSystemFeature") {
                    return
                }
                visitMethodCall(context, node, method)
            }
        }

    private fun isPackageManagerHasSystemFeature(method: PsiMethod): Boolean {
        if (method.name != "hasSystemFeature") {
            return false
        }
        val containingClass = method.containingClass?.qualifiedName ?: return false
        return containingClass == "android.content.pm.PackageManager"
    }
}