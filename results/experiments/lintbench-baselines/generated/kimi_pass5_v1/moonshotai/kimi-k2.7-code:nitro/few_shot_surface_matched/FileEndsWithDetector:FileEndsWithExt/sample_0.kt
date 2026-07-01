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
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

  companion object {
    private const val FILE_TYPE = "java.io.File"
    private const val STRING_TYPE = "java.lang.String"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "FileEndsWithExt",
        briefDescription = "File.endsWith checks path components, not string suffixes",
        explanation =
          """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components,
                not just string suffixes. For example, `File("foo.txt").endsWith(".txt")`
                returns false. If you want to test a file extension, use
                `file.path.endsWith(...)` or `file.extension.equals(...)`.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

  override fun getApplicableReferenceNames(): List<String> = listOf("endsWith")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (node.valueArgumentCount != 1) return

    val arg = node.valueArguments[0]
    if (!isStringType(arg.getExpressionType())) return

    val receiverType = node.receiver?.getExpressionType()
    if (isFileType(receiverType)) {
      report(context, node)
    }
  }

  override fun visitReference(context: JavaContext, node: UReferenceExpression) {
    if (node.uastParent is UCallExpression) return
    if (node !is UCallableReferenceExpression) return

    val method = node.resolve() as? PsiMethod ?: return
    if (isFileEndsWithStringMethod(method)) {
      val location = context.getLocation(node)
      val message =
        "`File.endsWith(String)` checks path components, not string suffixes. " +
          "Use `file.path.endsWith(...)` or `file.extension.equals(...)` instead."
      context.report(Incident(ISSUE, node, location, message))
    }
  }

  private fun isFileType(type: PsiType?): Boolean = type?.canonicalText == FILE_TYPE

  private fun isStringType(type: PsiType?): Boolean = type?.canonicalText == STRING_TYPE

  private fun isFileEndsWithStringMethod(method: PsiMethod): Boolean {
    if (method.name != "endsWith") return false
    val params = method.parameterList.parameters
    return params.size >= 2 && params.any { isFileType(it.type) } && params.any { isStringType(it.type) }
  }

  private fun report(context: JavaContext, node: UCallExpression) {
    val location = context.getLocation(node)
    val message =
      "`File.endsWith(String)` checks path components, not string suffixes. " +
        "Use `file.path.endsWith(...)` or `file.extension.equals(...)` instead."
    context.report(Incident(ISSUE, node, location, message))
  }
}