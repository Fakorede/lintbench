package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {
    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "FileEndsWithExt",
            "File endsWith on file extensions",
            "The Kotlin extension method `File.endsWith(suffix)` checks whole path components, not just string suffixes. " +
            "This means that `File(\"foo.txt\").endsWith(\".txt\")` will return false. " +
            "Instead you might have intended `file.path.endsWith` or `file.extension.equals`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiver = node.receiver ?: return
        val receiverType = context.evaluator.getType(receiver) ?: return
        val psiClass = (receiverType as? PsiClassType)?.resolve() ?: return
        if (psiClass.qualifiedName != "java.io.File") return

        val args = node.valueArguments
        if (args.size != 1) return

        val arg = args[0] as? ULiteralExpression ?: return
        val argValue = arg.value as? String ?: return
        if (argValue.startsWith(".")) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "Using `File.endsWith(\"$argValue\")` checks path components, not string suffixes. " +
                "Use `file.path.endsWith(\"$argValue\")` or check `file.extension` instead."
            )
        }
    }
}