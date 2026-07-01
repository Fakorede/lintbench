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

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File endsWith on file extensions",
            explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FileEndsWithDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("endsWith")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        val receiverType = node.receiverType
        val isFileReceiver = receiverType != null && evaluator.typeMatches(receiverType, "java.io.File")

        val isKotlinFileExtension = evaluator.isMemberInClass(method, "kotlin.io.FilesKt__UtilsKt") ||
                evaluator.isMemberInClass(method, "kotlin.io.FilesKt")

        if (!isFileReceiver && !isKotlinFileExtension) {
            return
        }

        val arguments = node.valueArguments
        val argumentToCheck = if (node.receiver != null) {
            arguments.firstOrNull()
        } else {
            if (arguments.size >= 2) arguments[1] else null
        } ?: return

        val stringValue = ConstantEvaluator.evaluate(context, argumentToCheck) as? String
        if (stringValue != null) {
            if (stringValue.startsWith(".") && stringValue.length > 1 && !stringValue.contains('/') && !stringValue.contains('\\')) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Use `file.path.endsWith(\"$stringValue\")` or `file.extension` instead of `File.endsWith` for extension checks"
                )
            }
        }
    }
}