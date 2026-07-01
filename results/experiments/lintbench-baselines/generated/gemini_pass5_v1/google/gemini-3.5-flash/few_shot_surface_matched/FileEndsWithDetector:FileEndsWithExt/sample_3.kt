package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "FileEndsWithExt",
            briefDescription = "File.endsWith() checks whole path components, not string suffixes",
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

    override fun getApplicableMethodNames(): List<String> {
        return listOf("endsWith")
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("endsWith")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression) {
        // Handled primarily by visitMethodCall
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val receiverType = node.receiverType ?: return
        if (!isFileType(context, receiverType)) {
            return
        }

        val argument = node.valueArguments.firstOrNull() ?: return
        val constant = argument.evaluate()
        if (constant is String && constant.startsWith(".")) {
            val message = "File.endsWith() checks whole path components, not string suffixes. Use File.path.endsWith() or File.extension instead."
            context.report(
                Incident(ISSUE, node, context.getLocation(argument), message)
            )
        }
    }

    private fun isFileType(context: JavaContext, type: PsiType): Boolean {
        val typeClass = context.evaluator.getTypeClass(type) ?: return false
        val qualifiedName = typeClass.qualifiedName
        return qualifiedName == "java.io.File" || context.evaluator.inheritsFrom(typeClass, "java.io.File", true)
    }
}