package com.android.tools.lint.checks

import com.android.sdklib.AndroidVersion
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

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "javax.crypto.Cipher")) {
            return
        }

        if (context.project.targetSdkVersion.apiLevel < AndroidVersion.VersionCodes.P) {
            return
        }

        val args = node.valueArguments
        if (args.size < 2) {
            return
        }

        val provider = ConstantEvaluator.evaluate(context, args[1]) as? String ?: return
        if (provider != "BC" && provider != "BouncyCastle") {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "The `BC` provider is deprecated and is not available when targetSdkVersion is P or later"
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
                Use a different provider or rely on the default provider instead.
            """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            moreInfo = "https://goo.gle/DeprecatedProvider",
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}