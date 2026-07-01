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
import org.jetbrains.uast.UExpression
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

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val containingClass = method.containingClass?.qualifiedName ?: return
        if (!containingClass.startsWith("kotlin.io.FilesKt")) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val stringValue = getConstantStringValue(argument)
        if (stringValue != null && stringValue.startsWith(".")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Using `File.endsWith` to check a file extension; this matches full path components, not suffixes"
            )
        }
    }

    private fun getConstantStringValue(node: UExpression): String? {
        val constant = node.evaluate()
        if (constant is String) {
            return constant
        }
        if (node is UCallExpression) {
            val resolvedMethod = node.resolve()
            if (resolvedMethod != null && resolvedMethod.isConstructor && resolvedMethod.containingClass?.qualifiedName == "java.io.File") {
                val fileArg = node.valueArguments.firstOrNull() ?: return null
                val fileArgVal = fileArg.evaluate()
                if (fileArgVal is String) {
                    return fileArgVal
                }
            }
        }
        return null
    }
}