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

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiverType = node.receiverType
        val isFileReceiver = receiverType?.canonicalText == "java.io.File"

        val isFileExtension = !isFileReceiver && 
            method.parameterList.parameters.firstOrNull()?.type?.canonicalText == "java.io.File"

        if (!isFileReceiver && !isFileExtension) return

        val suffixArg = if (isFileReceiver) {
            node.valueArguments.firstOrNull()
        } else {
            node.valueArguments.getOrNull(1)
        } ?: return

        val constantValue = suffixArg.evaluate() as? String
        if (constantValue != null && constantValue.startsWith(".") && constantValue.length > 1) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `file.path.endsWith(...)` or `file.extension` instead of `File.endsWith(...)` to match file extensions"
            )
        }
    }

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
}