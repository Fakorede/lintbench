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
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext,
        call: UCallExpression,
        visitor: UElementHandler
    ) {
        if (!context.evaluator.isMemberInClass(call.resolve(), "javax.crypto.Cipher")) {
            return
        }

        val args = call.valueArguments
        if (args.size != 2) {
            return
        }

        val provider = ConstantEvaluator.evaluate(context, args[1]) as? String ?: return
        if (provider != BC) {
            return
        }

        context.report(
            ISSUE,
            call,
            context.getLocation(call),
            "The `BC` provider has been deprecated and will not be provided " +
                    "when targetSdkVersion is P or higher."
        )
    }

    companion object {
        private const val BC = "BC"

        @JvmStatic
        val ISSUE: Issue = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = """
                The `BC` provider has been deprecated and will not be provided \
                when `targetSdkVersion` is P or higher. \
                Use `Cipher.getInstance(String, Provider)` with a `Provider` \
                instance instead.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                CipherGetInstanceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            moreInfo = "https://goo.gle/DeprecatedProvider"
        )
    }
}