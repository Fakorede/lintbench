package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class InternalInsetResourceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to \
                retrieve the relevant insets for your application. The insets are \
                dynamic values that can change while your app is visible, and your \
                app's window may not intersect with the system UI. To get the relevant \
                value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` \
                and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(InternalInsetResourceDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getIdentifier")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.content.res.Resources")) {
            return
        }

        val args = node.valueArguments
        if (args.size != 3) return

        val nameArg = args[0]
        val typeArg = args[1]
        val pkgArg = args[2]

        val name = ConstantEvaluator.evaluate(context, nameArg) as? String ?: return
        val type = ConstantEvaluator.evaluate(context, typeArg) as? String
        val pkg = ConstantEvaluator.evaluate(context, pkgArg) as? String

        val isAndroidPackage = pkg == "android" || name.startsWith("android:")
        if (!isAndroidPackage) return

        val resourceName = name.substringAfterLast("/")
        if (resourceName == "status_bar_height" ||
            resourceName == "navigation_bar_height" ||
            resourceName == "navigation_bar_height_landscape" ||
            resourceName == "navigation_bar_width" ||
            resourceName == "status_bar_height_portrait" ||
            resourceName == "status_bar_height_landscape"
        ) {
            val message = "Use `WindowInsetsCompat` to retrieve insets instead of using internal resources"
            context.report(Incident(ISSUE, node, context.getLocation(node), message))
        }
    }
}