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

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator

        val receiverType = node.receiverType
        val isFileReceiver = receiverType?.let { evaluator.isInstanceOf(it, "java.io.File", false) } ?: false

        val isStaticFileExtension = !isFileReceiver && method.parameterList.parametersCount > 0 &&
                evaluator.isInstanceOf(method.parameterList.parameters[0].type, "java.io.File", false)

        if (isFileReceiver || isStaticFileExtension) {
            val suffixArg = node.valueArguments.lastOrNull() ?: return
            val suffixValue = suffixArg.evaluate() as? String
            if (suffixValue != null && suffixValue.startsWith(".") && suffixValue != "." && suffixValue != "..") {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(suffixArg),
                    "Using `File.endsWith` to match file extensions is bug-prone. " +
                            "It checks whole path components, not string suffixes. " +
                            "Use `file.path.endsWith` or `file.extension.equals` instead."
                )
            }
        }
    }
}