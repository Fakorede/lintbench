package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
            implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val receiver = node.receiver ?: return
        val receiverType = evaluator.getType(receiver) ?: return

        if (!evaluator.typeMatches(receiverType, "java.io.File")) return

        val args = node.valueArguments
        if (args.size != 1) return

        val argType = evaluator.getType(args[0]) ?: return
        if (!evaluator.typeMatches(argType, "java.lang.String")) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Using `File.endsWith()` checks path components, not string suffixes. " +
                "Use `file.path.endsWith()` or `file.extension == ...` instead."
        )
    }
}