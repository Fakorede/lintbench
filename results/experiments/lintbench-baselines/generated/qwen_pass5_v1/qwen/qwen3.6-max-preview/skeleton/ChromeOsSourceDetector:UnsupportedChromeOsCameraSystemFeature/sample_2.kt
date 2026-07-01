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
            explanation = "Looking for `FEATURE_CAMERA` only looks for a rear facing camera, which certain large screen devices don't have, as well as newer device configurations and modes may place the device in a state where the rear camera is not available. To fix the issue, look for `FEATURE_CAMERA_ANY` instead.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
        
        private const val CAMERA_FEATURE = "android.hardware.camera"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UCallExpression::class.java)

    override fun getApplicableMethodNames(): List<String>? = listOf("hasSystemFeature")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        checkCameraFeature(context, node, method)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not applicable for this detector
            }
    
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                checkCameraFeature(context, node, method)
            }
        }
        
    private fun checkCameraFeature(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            return
        }
        
        val args = node.valueArguments
        if (args.isEmpty()) return

        val firstArg = args[0]
        val stringValue = evaluator.getStringValue(firstArg)
        
        if (stringValue == CAMERA_FEATURE) {
            context.report(
                ISSUE,
                node,
                context.getLocation(firstArg),
                "Use `FEATURE_CAMERA_ANY` instead of `FEATURE_CAMERA` to support devices without a rear-facing camera."
            )
        }
    }
}