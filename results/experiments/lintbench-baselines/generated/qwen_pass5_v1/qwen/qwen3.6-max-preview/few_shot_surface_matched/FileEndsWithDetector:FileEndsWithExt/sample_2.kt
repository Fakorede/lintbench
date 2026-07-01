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
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReference

class FileEndsWithDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val FILE_ENDS_WITH_EXT = Issue.create(
      id = "FileEndsWithExt",
      briefDescription = "File.endsWith checks path components, not string suffixes",
      explanation = """
        The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
        not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
        false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)
    )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

  override fun getApplicableReferenceNames(): List<String> = listOf("endsWith")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val receiver = node.receiver ?: return
    val type = context.evaluator.getType(receiver) ?: return
    if (type.canonicalText.removeSuffix("?") == "java.io.File") {
      reportIssue(context, node)
    }
  }

  override fun visitReference(context: JavaContext, reference: UReference) {
    val parent = reference.uastParent as? UQualifiedReferenceExpression ?: return
    val receiverType = context.evaluator.getType(parent.receiver) ?: return
    if (receiverType.canonicalText.removeSuffix("?") == "java.io.File") {
      reportIssue(context, reference)
    }
  }

  private fun reportIssue(context: JavaContext, node: org.jetbrains.uast.UElement) {
    val message = "File.endsWith(suffix) checks path components, not string suffixes. " +
      "Use file.path.endsWith(suffix) or file.extension == suffix instead."
    context.report(Incident(FILE_ENDS_WITH_EXT, node, context.getLocation(node), message))
  }
}