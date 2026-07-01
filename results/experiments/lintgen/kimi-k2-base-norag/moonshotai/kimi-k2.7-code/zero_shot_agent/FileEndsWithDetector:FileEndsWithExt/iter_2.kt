package com.android.tools.lint.checks

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
import org.jetbrains.uast.UExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val evaluator = context.evaluator
        val receiver = node.receiver
        val isFileReceiver: Boolean
        val suffixCandidates: List<UExpression>

        if (receiver != null) {
            val receiverType = receiver.getExpressionType() ?: return
            val receiverClass = evaluator.getTypeClass(receiverType) ?: return
            isFileReceiver = evaluator.extendsClass(receiverClass, "java.io.File", false)
            suffixCandidates = node.valueArguments
        } else {
            val args = node.valueArguments
            if (args.isEmpty()) return
            val first = args[0]
            val firstType = first.getExpressionType() ?: return
            val firstClass = evaluator.getTypeClass(firstType) ?: return
            isFileReceiver = evaluator.extendsClass(firstClass, "java.io.File", false)
            if (isFileReceiver) {
                val containingClass = method.containingClass?.qualifiedName ?: return
                if (!containingClass.startsWith("kotlin.io.FilesKt")) return
            }
            suffixCandidates = args.drop(1)
        }

        if (!isFileReceiver) return

        for (arg in suffixCandidates) {
            val value = ConstantEvaluator.evaluate(context, arg) as? String ?: continue
            if (value.startsWith('.')) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Using `File.endsWith` to check a file extension will not work as expected; " +
                        "it checks whole path components, not string suffixes. " +
                        "Use `file.path.endsWith(...)` or `file.extension == ...` instead."
                )
                return
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components,
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will
                return false. If you want to check a file extension, use `file.path.endsWith(...)`
                or `file.extension` instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FileEndsWithDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}