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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "FileEndsWithExt",
        briefDescription = "File endsWith on file extensions",
        explanation =
          """
                Calling `File.endsWith(suffix)` checks whole path components, not just string suffixes.
                For example, `File("foo.txt").endsWith(".txt")` returns false.

                If you intended to test the file name as a string, use `file.path.endsWith(suffix)`.
                If you intended to compare file extensions, use `file.extension` (or `file.extension.equals("txt")`).
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

  override fun getApplicableReferenceNames(): List<String> = emptyList()

  override fun visitReference(context: JavaContext, reference: UReferenceExpression) {
    // No reference-only checks are required for this issue.
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val receiver = node.receiver ?: return
    val receiverType = receiver.getExpressionType() ?: return
    if (!context.evaluator.typeMatches(receiverType, "java.io.File")) {
      return
    }

    val argument = node.valueArguments.firstOrNull() ?: return
    val literal = argument as? ULiteralExpression ?: return
    val value = literal.value as? String ?: return
    if (!value.startsWith(".")) {
      return
    }

    val message =
      "Using File.endsWith(\"$value\") checks whole path components, not string suffixes. " +
        "Consider using file.path.endsWith(\"$value\") or file.extension instead."
    context.report(Incident(ISSUE, node, context.getLocation(node), message))
  }
}