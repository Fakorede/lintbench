package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class SecureRandomDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "SecureRandom",
        briefDescription = "Using a fixed seed with SecureRandom",
        explanation =
          """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not appropriate \
                for secure use.
            """,
        category = Category.SECURITY,
        priority = 4,
        severity = Severity.WARNING,
        implementation = Implementation(SecureRandomDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  override fun getApplicableMethodNames() = listOf("setSeed")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "java.security.SecureRandom")) {
      return
    }

    val seed = node.valueArguments.firstOrNull() ?: return
    if (ConstantEvaluator.evaluate(seed) == null) {
      return
    }

    val message = "Using a fixed seed with SecureRandom will produce predictable random numbers"
    context.report(ISSUE, node, context.getLocation(node), message)
  }
}