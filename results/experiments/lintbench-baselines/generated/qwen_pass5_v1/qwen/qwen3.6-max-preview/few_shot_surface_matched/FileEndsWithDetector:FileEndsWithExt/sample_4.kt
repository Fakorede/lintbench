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
    @JvmField
    val ISSUE = Issue.create(
      id = "FileEndsWithExt",
      briefDescription = "File.endsWith checks path components, not string suffixes",
      explanation =
        """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
            """,
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE),
    )
  }

  override fun getApplicableMethodNames(): List<String>? = listOf("endsWith")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val receiverType = node.receiver?.getExpressionType()?.canonicalText
    if (receiverType == "java.io.File" || receiverType == "java.io.File?") {
      val args = node.valueArguments
      if (args.size == 1 && args[0].getExpressionType()?.canonicalText?.endsWith("String") == true) {
        val location = context.getLocation(node)
        val message =
          "File.endsWith(suffix) checks whole path components, not string suffixes. Use file.path.endsWith(suffix) or file.extension == suffix instead."
        context.report(Incident(ISSUE, node, location, message))
      }
    }
  }

  override fun getApplicableReferenceNames(): List<String>? = listOf("endsWith")

  override fun visitReference(context: JavaContext, node: UReferenceExpression, resolved: PsiElement) {
    if (resolved is PsiMethod && resolved.name == "endsWith") {
      val params = resolved.parameterList.parameters
      val paramType = params.firstOrNull()?.type?.canonicalText
      if (paramType == "java.io.File" || paramType == "java.io.File?") {
        val location = context.getLocation(node)
        val message =
          "File.endsWith(suffix) checks whole path components, not string suffixes. Use file.path.endsWith(suffix) or file.extension == suffix instead."
        context.report(Incident(ISSUE, node, location, message))
      }
    }
  }
}