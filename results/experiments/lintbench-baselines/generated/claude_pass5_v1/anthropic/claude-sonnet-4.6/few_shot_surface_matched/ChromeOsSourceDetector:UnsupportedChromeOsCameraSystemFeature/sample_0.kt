package com.android.tools.lint.checks

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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val HAS_SYSTEM_FEATURE_METHOD = "hasSystemFeature"

        private val MESSAGE =
            "You should look for the `FEATURE_CAMERA_ANY` features to include all possible " +
                "cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks " +
                "for a rear facing camera, which certain large screen devices don't have, " +
                "as well as newer device configurations and modes may place the device " +
                "in a state where the rear camera is not available. To fix the issue, " +
                "look for `FEATURE_CAMERA_ANY` instead."

        @JvmField
        val UNSUPPORTED_CAMERA_FEATURE =
            Issue.create(
                id = "UnsupportedChromeOsCameraSystemFeature",
                briefDescription = "Looking for Rear Camera only feature",
                explanation =
                    """
                    You should look for the `FEATURE_CAMERA_ANY` features to include all possible \
                    cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks \
                    for a rear facing camera, which certain large screen devices don't have, \
                    as well as newer device configurations and modes may place the device \
                    in a state where the rear camera is not available. To fix the issue, \
                    look for `FEATURE_CAMERA_ANY` instead.
                    """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                implementation =
                    Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
                androidSpecific = true,
            )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(HAS_SYSTEM_FEATURE_METHOD)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val firstArgument = node.valueArguments.firstOrNull() ?: return

        val resolvedValue = resolveStringValue(context, firstArgument)

        if (resolvedValue == FEATURE_CAMERA) {
            context.report(
                UNSUPPORTED_CAMERA_FEATURE,
                node,
                context.getLocation(firstArgument),
                MESSAGE,
            )
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) =
        object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                // Primary logic is handled in visitMethodCall; this is a no-op handler
                return super.visitCallExpression(node)
            }
        }

    private fun resolveStringValue(context: JavaContext, element: UElement): String? {
        // Try to evaluate the expression as a constant
        val evaluated =
            when (element) {
                is UCallExpression -> null
                else ->
                    try {
                        context.evaluator.let {
                            (element as? org.jetbrains.uast.UExpression)
                                ?.evaluate()
                                ?.let { v -> v as? String }
                        }
                    } catch (e: Exception) {
                        null
                    }
            }

        if (evaluated != null) return evaluated

        // Try to resolve field references like PackageManager.FEATURE_CAMERA
        if (element is UQualifiedReferenceExpression) {
            val selector = element.selector
            if (selector is USimpleNameReferenceExpression) {
                val resolved = selector.resolve()
                if (resolved is com.intellij.psi.PsiField) {
                    val value = resolved.computeConstantValue()
                    if (value is String) return value
                }
            }
        }

        if (element is USimpleNameReferenceExpression) {
            val resolved = element.resolve()
            if (resolved is com.intellij.psi.PsiField) {
                val value = resolved.computeConstantValue()
                if (value is String) return value
            }
        }

        // Fallback: try evaluate on the expression itself
        return (element as? org.jetbrains.uast.UExpression)?.evaluate() as? String
    }
}