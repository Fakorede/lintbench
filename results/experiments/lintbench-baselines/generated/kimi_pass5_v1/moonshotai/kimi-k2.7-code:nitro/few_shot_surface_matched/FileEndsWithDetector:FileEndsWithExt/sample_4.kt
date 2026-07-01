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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val JAVA_IO_FILE = "java.io.File"

    @JvmField
    val ISSUE: Issue = Issue.create(
      id = "FileEndsWithExt",
      briefDescription = "File.endsWith checks whole path components, not file extensions",
      explanation = """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components,
                not string suffixes. For example, `File("foo.txt").endsWith(".txt")` returns false.
                If you want to check a file extension, use `file.path.endsWith(suffix)` or
                `file.extension == "txt"` instead.
            """,
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = false,
    )
  }

  override fun getApplicableMethodNames() = listOf("endsWith")

  override fun getApplicableReferenceNames() = emptyList<String>()

  override fun visitReference(
    context: JavaContext,
    node: UReferenceExpression,
    referenced: PsiElement,
  ) {
    // No reference-based checks are needed for this issue.
  }

  override fun visitMethodCall(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod,
  ) {
    if (method.name != "endsWith") return

    val receiver = node.receiver ?: return
    val receiverType = receiver.getExpressionType() ?: return
    if (receiverType.canonicalText != JAVA_IO_FILE) return

    val argument = node.valueArguments.firstOrNull() ?: return
    val suffix = argument.evaluate() as? String ?: return
    if (!suffix.startsWith(".")) return

    val location = context.getLocation(node)
    val message =
      "`File.endsWith` checks whole path components, not string suffixes. " +
        "Did you mean `file.path.endsWith(\"$suffix\")` or `file.extension == \"${suffix.substring(1)}\"`?"
    context.report(Incident(ISSUE, node, location, message))
  }
}