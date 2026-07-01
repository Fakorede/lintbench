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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val HAS_SYSTEM_FEATURE_METHOD = "hasSystemFeature"

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
                implementation = Implementation(
                    ChromeOsSourceDetector::class.java,
                    Scope.JAVA_FILE_SCOPE
                ),
                androidSpecific = true,
            )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(HAS_SYSTEM_FEATURE_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val firstArgument = node.valueArguments.firstOrNull() ?: return

        val argumentValue: String? = when (firstArgument) {
            is ULiteralExpression -> firstArgument.value as? String
            is UReferenceExpression -> {
                val resolved = firstArgument.resolve()
                if (resolved is com.intellij.psi.PsiField) {
                    val constantValue = resolved.computeConstantValue()
                    constantValue as? String
                } else {
                    null
                }
            }
            else -> null
        }

        if (argumentValue == FEATURE_CAMERA) {
            val location = context.getLocation(firstArgument)
            val message =
                "You should look for the `FEATURE_CAMERA_ANY` feature to include all possible " +
                "cameras that may be on the device. `FEATURE_CAMERA` only looks for a rear " +
                "facing camera, which certain large screen devices don't have. " +
                "Use `PackageManager.FEATURE_CAMERA_ANY` instead."
            context.report(UNSUPPORTED_CAMERA_FEATURE, node, location, message)
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UastVisitor {
        return object : UastVisitor {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName ?: return false
                if (methodName != HAS_SYSTEM_FEATURE_METHOD) return false

                val method = node.resolve() ?: return false
                if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) {
                    return false
                }

                val firstArgument = node.valueArguments.firstOrNull() ?: return false

                val argumentValue: String? = when (firstArgument) {
                    is ULiteralExpression -> firstArgument.value as? String
                    is UReferenceExpression -> {
                        val resolved = firstArgument.resolve()
                        if (resolved is com.intellij.psi.PsiField) {
                            resolved.computeConstantValue() as? String
                        } else {
                            null
                        }
                    }
                    else -> null
                }

                if (argumentValue == FEATURE_CAMERA) {
                    val location = context.getLocation(firstArgument)
                    val message =
                        "You should look for the `FEATURE_CAMERA_ANY` feature to include all possible " +
                        "cameras that may be on the device. `FEATURE_CAMERA` only looks for a rear " +
                        "facing camera, which certain large screen devices don't have. " +
                        "Use `PackageManager.FEATURE_CAMERA_ANY` instead."
                    context.report(UNSUPPORTED_CAMERA_FEATURE, node, location, message)
                }

                return false
            }

            override fun visitMethod(node: org.jetbrains.uast.UMethod): Boolean = false

            override fun visitElement(node: UElement): Boolean = false
        }
    }
}