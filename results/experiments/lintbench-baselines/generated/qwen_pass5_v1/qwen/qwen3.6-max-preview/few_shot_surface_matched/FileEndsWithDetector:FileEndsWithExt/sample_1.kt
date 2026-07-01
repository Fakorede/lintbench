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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression

class FileEndsWithDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val FILE_ENDS_WITH_EXT =
      Issue.create(
        id = "FileEndsWithExt",
        briefDescription = "File.endsWith checks path components, not string suffixes",
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

  override fun getApplicableMethodNames() = listOf("endsWith")

  override fun getApplicableReferenceNames() = listOf("endsWith")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val receiverType = node.receiver?.getExpressionType() ?: return
    if (!context.evaluator.extendsClass(receiverType, "java.io.File", false)) return

    val message =
      "File.endsWith(suffix) checks whole path components, not string suffixes. Use file.path.endsWith(suffix) or file.extension == \"ext\" instead."
    context.report(Incident(FILE_ENDS_WITH_EXT, node, context.getLocation(node), message))
  }

  override fun visitReference(context: JavaContext, node: UReferenceExpression, referenced: PsiElement) {
    val receiverType = (node as? UQualifiedReferenceExpression)?.receiver?.getExpressionType() ?: return
    if (!context.evaluator.extendsClass(receiverType, "java.io.File", false)) return

    val message =
      "File.endsWith(suffix) checks whole path components, not string suffixes. Use file.path.endsWith(suffix) or file.extension == \"ext\" instead."
    context.report(Incident(FILE_ENDS_WITH_EXT, node, context.getLocation(node), message))
  }
}