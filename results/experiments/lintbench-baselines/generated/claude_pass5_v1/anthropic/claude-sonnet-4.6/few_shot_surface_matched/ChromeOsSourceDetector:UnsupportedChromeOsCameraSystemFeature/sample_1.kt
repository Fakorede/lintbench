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
import org.jetbrains.uast.UastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val HAS_SYSTEM_FEATURE_METHOD = "hasSystemFeature"

        private val UNSUPPORTED_CAMERA_FEATURE_FIELD = "FEATURE_CAMERA"

        @JvmField
        val UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE = Issue.create(
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

        val featureValue: String? = when (firstArgument) {
            is ULiteralExpression -> firstArgument.value as? String
            is UReferenceExpression -> {
                val resolved = firstArgument.resolve()
                if (resolved is com.intellij.psi.PsiField) {
                    val containingClass = resolved.containingClass?.qualifiedName
                    if (containingClass == PACKAGE_MANAGER_CLASS && resolved.name == UNSUPPORTED_CAMERA_FEATURE_FIELD) {
                        FEATURE_CAMERA
                    } else {
                        val value = resolved.computeConstantValue()
                        value as? String
                    }
                } else {
                    null
                }
            }
            else -> null
        }

        if (featureValue == FEATURE_CAMERA) {
            val location = context.getLocation(firstArgument)
            context.report(
                UNSUPPORTED_CHROME_OS_CAMERA_SYSTEM_FEATURE,
                node,
                location,
                "You should look for the `FEATURE_CAMERA_ANY` features to include all " +
                    "possible cameras that may be on the device. Looking for `FEATURE_CAMERA` " +
                    "only looks for a rear facing camera, which certain large screen devices " +
                    "don't have, as well as newer device configurations and modes may place " +
                    "the device in a state where the rear camera is not available. To fix " +
                    "the issue, look for `FEATURE_CAMERA_ANY` instead."
            )
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UastVisitor {
        override fun visitCallExpression(node: UCallExpression): Boolean {
            return false
        }
    }
}