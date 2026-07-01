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

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (!containingClass.startsWith("kotlin.io.FilesKt")) {
            return
        }

        val lastArg = node.valueArguments.lastOrNull() ?: return
        var suffix: String? = ConstantEvaluator.evaluate(context, lastArg) as? String

        if (suffix == null && lastArg is UCallExpression) {
            val resolved = lastArg.resolve()
            if (resolved?.isConstructor == true && resolved.containingClass?.qualifiedName == "java.io.File") {
                val fileArg = lastArg.valueArguments.firstOrNull()
                if (fileArg != null) {
                    suffix = ConstantEvaluator.evaluate(context, fileArg) as? String
                }
            }
        }

        if (suffix != null && suffix.startsWith(".") && suffix.length > 1) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `File.endsWith` to check a file extension will return false; it checks whole path components. Use `file.path.endsWith` or `file.extension.equals` instead."
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