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

        @JvmField
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
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf(HAS_SYSTEM_FEATURE_METHOD)

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val firstArg = node.valueArguments.firstOrNull() ?: return
        val argValue = resolveStringValue(firstArg)

        if (argValue == FEATURE_CAMERA) {
            context.report(
                UNSUPPORTED_CAMERA_FEATURE,
                node,
                context.getLocation(firstArg),
                "Use `PackageManager.FEATURE_CAMERA_ANY` to detect any camera, " +
                    "including front-facing cameras on devices that may not have a rear-facing camera. " +
                    "`FEATURE_CAMERA` only detects rear-facing cameras."
            )
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UQualifiedReferenceExpression::class.java, USimpleNameReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : AbstractUastVisitor() {

        override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean {
            checkForFeatureCameraConstant(context, node)
            return super.visitQualifiedReferenceExpression(node)
        }

        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
            checkForFeatureCameraConstant(context, node)
            return super.visitSimpleNameReferenceExpression(node)
        }
    }

    private fun checkForFeatureCameraConstant(context: JavaContext, node: UElement) {
        val resolved = when (node) {
            is UQualifiedReferenceExpression -> node.selector.let { selector ->
                if (selector is USimpleNameReferenceExpression) selector.resolveToUElement() else null
            }
            is USimpleNameReferenceExpression -> node.resolveToUElement()
            else -> null
        } ?: return

        val psiField = (resolved as? org.jetbrains.uast.UField)?.javaPsi ?: return
        val containingClass = psiField.containingClass?.qualifiedName ?: return

        if (containingClass == PACKAGE_MANAGER_CLASS && psiField.name == "FEATURE_CAMERA") {
            val parentCall = node.uastParent
            if (parentCall is UCallExpression) return
            if (parentCall is UQualifiedReferenceExpression && parentCall.uastParent is UCallExpression) return

            context.report(
                UNSUPPORTED_CAMERA_FEATURE,
                node,
                context.getLocation(node),
                "Use `PackageManager.FEATURE_CAMERA_ANY` to detect any camera, " +
                    "including front-facing cameras on devices that may not have a rear-facing camera. " +
                    "`FEATURE_CAMERA` only detects rear-facing cameras."
            )
        }
    }

    private fun resolveStringValue(expression: org.jetbrains.uast.UExpression): String? {
        val evaluated = expression.evaluate()
        if (evaluated is String) return evaluated

        val reference = when (expression) {
            is UQualifiedReferenceExpression -> expression.selector as? USimpleNameReferenceExpression
            is USimpleNameReferenceExpression -> expression
            else -> null
        } ?: return null

        val resolved = reference.resolveToUElement()
        val field = (resolved as? org.jetbrains.uast.UField)?.javaPsi ?: return null
        val containingClass = field.containingClass?.qualifiedName ?: return null

        if (containingClass == PACKAGE_MANAGER_CLASS) {
            return when (field.name) {
                "FEATURE_CAMERA" -> FEATURE_CAMERA
                "FEATURE_CAMERA_ANY" -> FEATURE_CAMERA_ANY
                else -> null
            }
        }
        return null
    }
}