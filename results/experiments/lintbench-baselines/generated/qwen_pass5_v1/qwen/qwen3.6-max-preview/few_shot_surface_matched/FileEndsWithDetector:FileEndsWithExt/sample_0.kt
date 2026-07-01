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
    val FILE_ENDS_WITH_EXT =
      Issue.create(
        id = "FileEndsWithExt",
        briefDescription = "File.endsWith checks path components, not string suffix",
        explanation =
          """
            The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
            not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
            false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
          """.trimIndent(),
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("endsWith")
  override fun getApplicableReferenceNames(): List<String> = listOf("endsWith")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!isFileEndsWith(context, method, node)) return
    val location = context.getLocation(node)
    val message =
      "File.endsWith(suffix) checks path components, not string suffixes. Use file.path.endsWith(suffix) or file.extension == suffix instead."
    context.report(Incident(FILE_ENDS_WITH_EXT, node, location, message))
  }

  override fun visitReference(context: JavaContext, node: UReferenceExpression, resolved: PsiElement) {
    if (resolved !is PsiMethod) return
    if (!isFileEndsWith(context, resolved, null)) return
    val location = context.getLocation(node)
    val message =
      "File.endsWith(suffix) checks path components, not string suffixes. Use file.path.endsWith(suffix) or file.extension == suffix instead."
    context.report(Incident(FILE_ENDS_WITH_EXT, node, location, message))
  }

  private fun isFileEndsWith(context: JavaContext, method: PsiMethod, call: UCallExpression?): Boolean {
    if (method.name != "endsWith") return false
    val params = method.parameterList.parameters
    if (params.size != 1) return false
    val paramType = params[0].type.canonicalText
    if (paramType != "java.lang.String" && paramType != "kotlin.String") return false

    val qName = context.evaluator.getQualifiedName(method)
    if (qName == "kotlin.io.endsWith") return true

    val receiverType = call?.receiver?.getExpressionType()?.canonicalText
    return receiverType == "java.io.File"
  }
}