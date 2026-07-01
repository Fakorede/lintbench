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
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression

class ChromeOsSourceDetector : Detector(), Detector.SourceCodeScanner {

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
                This check looks for calls to `PackageManager.hasSystemFeature(String)` that use
                `PackageManager.FEATURE_CAMERA`. `FEATURE_CAMERA` only indicates the presence of a
                rear-facing camera, which many large-screen devices (such as Chromebooks) do not
                have. Newer device configurations and modes may also place the device in a state
                where the rear camera is unavailable. Use `PackageManager.FEATURE_CAMERA_ANY`
                instead, which reports `true` if the device has any camera.
            """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val PACKAGE_MANAGER = "android.content.pm.PackageManager"
        private const val FEATURE_CAMERA = "android.hardware.camera"
        private const val METHOD_HAS_SYSTEM_FEATURE = "hasSystemFeature"
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf<Class<out UElement>>(UCallExpression::class.java)

    override fun getApplicableMethodNames(): List<String>? =
        listOf(METHOD_HAS_SYSTEM_FEATURE)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        checkMethodCall(context, node)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                // Not needed for this check.
            }

            override fun visitCallExpression(node: UCallExpression) {
                checkMethodCall(context, node)
            }
        }

    private fun checkMethodCall(context: JavaContext, node: UCallExpression) {
        val method = node.resolve() ?: return
        if (method.name != METHOD_HAS_SYSTEM_FEATURE) return

        val containingClass = method.containingClass ?: return
        if (containingClass.qualifiedName != PACKAGE_MANAGER) return

        val args = node.valueArguments
        if (args.isEmpty()) return

        val featureArg = args[0]
        val feature = ConstantEvaluator.evaluate(context, featureArg) as? String
            ?: resolveFeatureReference(featureArg)

        if (feature == FEATURE_CAMERA) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Looking for a rear-facing camera only. Use PackageManager.FEATURE_CAMERA_ANY instead."
            )
        }
    }

    private fun resolveFeatureReference(arg: org.jetbrains.uast.UExpression): String? {
        val ref = arg as? UReferenceExpression ?: return null
        val resolved = ref.resolve() as? PsiField ?: return null
        val containingClass = resolved.containingClass ?: return null
        return if (containingClass.qualifiedName == PACKAGE_MANAGER && resolved.name == "FEATURE_CAMERA") {
            FEATURE_CAMERA
        } else {
            null
        }
    }
}