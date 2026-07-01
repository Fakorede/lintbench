package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            FileEndsWithDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("endsWith")
    }

    override fun getApplicableReferenceNames(): List<String>? {
        return null
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        // No-op
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        val parameters = method.parameterList.parameters

        val isFileEndsWith = node.receiverType?.canonicalText == "java.io.File" ||
                evaluator.isMemberInClass(method, "kotlin.io.FilesKt__UtilsKt") ||
                evaluator.isMemberInClass(method, "kotlin.io.FilesKt") ||
                (parameters.firstOrNull()?.type?.canonicalText == "java.io.File")

        if (!isFileEndsWith) {
            return
        }

        val suffixArg = node.valueArguments.lastOrNull() ?: return
        val suffixValue = suffixArg.evaluate() as? String
        if (suffixValue != null && suffixValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use `file.path.endsWith` or `file.extension.equals` instead of `File.endsWith` with a file extension"
            )
        }
    }
}