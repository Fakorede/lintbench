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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.evaluateString

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val HAS_SYSTEM_FEATURE_METHOD = "hasSystemFeature"
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_FIELD = "FEATURE_CAMERA"
        private const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"
        private const val FEATURE_CAMERA_ANY_FIELD = "FEATURE_CAMERA_ANY"

        val UNSUPPORTED_CAMERA_FEATURE = Issue.create(
            id = "UnsupportedChromeOsCameraSystemFeature",
            briefDescription = "Looking for Rear Camera only feature",
            explanation = """
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
            moreInfo = "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support",
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(HAS_SYSTEM_FEATURE_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.inheritsFrom(containingClass, PACKAGE_MANAGER_CLASS, false)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) return

        val firstArg = arguments[0]

        val featureValue = resolveFeatureValue(firstArg)
        if (featureValue != null && featureValue == FEATURE_CAMERA) {
            context.report(
                UNSUPPORTED_CAMERA_FEATURE,
                node,
                context.getLocation(firstArg),
                "Use `PackageManager.FEATURE_CAMERA_ANY` to detect any camera, including " +
                        "front-facing cameras, which are the only cameras available on some " +
                        "large screen devices"
            )
        }
    }

    private fun resolveFeatureValue(element: UElement): String? {
        // Handle string literal directly
        if (element is ULiteralExpression) {
            return element.evaluateString()
        }

        // Handle qualified reference like PackageManager.FEATURE_CAMERA
        if (element is UQualifiedReferenceExpression) {
            val selector = element.selector
            if (selector is UReferenceExpression) {
                val fieldName = selector.resolvedName
                if (fieldName == FEATURE_CAMERA_FIELD) {
                    return FEATURE_CAMERA
                }
                if (fieldName == FEATURE_CAMERA_ANY_FIELD) {
                    return FEATURE_CAMERA_ANY
                }
            }
            // Try evaluating as a constant
            val evaluated = element.evaluate()
            if (evaluated is String) {
                return evaluated
            }
        }

        // Handle simple reference like FEATURE_CAMERA (imported statically)
        if (element is UReferenceExpression) {
            val fieldName = element.resolvedName
            if (fieldName == FEATURE_CAMERA_FIELD) {
                // Verify it's from PackageManager
                val resolved = element.resolve()
                if (resolved is com.intellij.psi.PsiField) {
                    val containingClass = resolved.containingClass
                    if (containingClass?.qualifiedName == PACKAGE_MANAGER_CLASS) {
                        return FEATURE_CAMERA
                    }
                }
            }
            if (fieldName == FEATURE_CAMERA_ANY_FIELD) {
                val resolved = element.resolve()
                if (resolved is com.intellij.psi.PsiField) {
                    val containingClass = resolved.containingClass
                    if (containingClass?.qualifiedName == PACKAGE_MANAGER_CLASS) {
                        return FEATURE_CAMERA_ANY
                    }
                }
            }
            // Try evaluating as a constant
            val evaluated = element.evaluate()
            if (evaluated is String) {
                return evaluated
            }
        }

        return null
    }
}