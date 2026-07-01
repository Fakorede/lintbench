package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
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
                as well as newer device configurations and modes may place the device \
                in a state where the rear camera is not available. To fix the issue, \
                look for `FEATURE_CAMERA_ANY` instead.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val HAS_SYSTEM_FEATURE_METHOD = "hasSystemFeature"
        private const val FEATURE_CAMERA_VALUE = "android.hardware.camera"
        private const val FEATURE_CAMERA_FIELD = "FEATURE_CAMERA"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = null

    override fun getApplicableMethodNames(): List<String> = listOf(HAS_SYSTEM_FEATURE_METHOD)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Verify the method is called on PackageManager
        if (!context.evaluator.isMemberInClass(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val arguments = node.valueArguments
        if (arguments.isEmpty()) {
            return
        }

        val firstArg = arguments[0]

        // Evaluate the argument value to check if it equals "android.hardware.camera"
        val evaluatedValue = context.evaluator.getConstantValue(firstArg)
        if (evaluatedValue is String && evaluatedValue.equals(FEATURE_CAMERA_VALUE, ignoreCase = true)) {
            reportIssue(context, node)
            return
        }

        // Also check by resolving field references to PackageManager.FEATURE_CAMERA
        val resolvedText = firstArg.asSourceString()
        if (resolvedText.contains(FEATURE_CAMERA_FIELD) && !resolvedText.contains("FEATURE_CAMERA_ANY")) {
            // Try to resolve the field reference
            val psiReference = firstArg.sourcePsi?.reference
            if (psiReference != null) {
                val resolved = psiReference.resolve()
                if (resolved is com.intellij.psi.PsiField) {
                    val containingClass = resolved.containingClass
                    if (containingClass?.qualifiedName == PACKAGE_MANAGER_CLASS &&
                        resolved.name == FEATURE_CAMERA_FIELD) {
                        reportIssue(context, node)
                    }
                }
            }
        }
    }

    private fun reportIssue(context: JavaContext, node: UCallExpression) {
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getLocation(node),
            message = "You should look for `FEATURE_CAMERA_ANY` to include all cameras " +
                "available on the device, rather than `FEATURE_CAMERA` which only looks " +
                "for a rear-facing camera, which may not be available on all devices.",
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // No additional method-level analysis needed
            }

            override fun visitCallExpression(node: UCallExpression) {
                // Handled via visitMethodCall
            }
        }
}