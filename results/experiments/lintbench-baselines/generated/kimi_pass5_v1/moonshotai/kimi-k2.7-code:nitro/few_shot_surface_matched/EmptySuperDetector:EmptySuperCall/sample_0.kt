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
import org.jetbrains.uast.AbstractUastVisitor
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UastUtils

class EmptySuperDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val EMPTY_SUPER_CALL = Issue.create(
      id = "EmptySuperCall",
      briefDescription = "Calling an empty super method",
      explanation = """
                Methods annotated with `@EmptySuper` indicate that the super implementation is empty
                or should not be invoked by overriding methods. Overriding methods should not call
                `super.<method>()`.
            """,
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.WARNING,
      implementation = Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE),
    )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UMethod::class.java, UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      private val reportedCalls = mutableSetOf<UCallExpression>()

      override fun visitMethod(node: UMethod) {
        val psiMethod = node.javaPsi ?: return
        val superMethod = context.evaluator.getSuperMethod(psiMethod) ?: return
        if (!hasEmptySuperAnnotation(superMethod)) return

        val body = node.uastBody ?: return
        body.accept(object : AbstractUastVisitor() {
          override fun visitCallExpression(call: UCallExpression): Boolean {
            if (UastUtils.getParentOfType(call, UMethod::class.java, true) != node) {
              return super.visitCallExpression(call)
            }
            checkCall(context, call, superMethod, reportedCalls)
            return super.visitCallExpression(call)
          }
        })
      }

      override fun visitCallExpression(node: UCallExpression) {
        val method = UastUtils.getParentOfType(node, UMethod::class.java, true) ?: return
        val psiMethod = method.javaPsi ?: return
        val superMethod = context.evaluator.getSuperMethod(psiMethod) ?: return
        if (!hasEmptySuperAnnotation(superMethod)) return
        checkCall(context, node, superMethod, reportedCalls)
      }
    }
  }

  private fun hasEmptySuperAnnotation(method: PsiMethod): Boolean {
    return method.modifierList.annotations.any { annotation ->
      val name = annotation.qualifiedName
      name == "EmptySuper" || name?.endsWith(".EmptySuper") == true
    }
  }

  private fun isSuperCall(call: UCallExpression): Boolean {
    if (call.receiver is USuperExpression) return true
    val parent = call.uastParent
    if (parent is UQualifiedReferenceExpression && parent.receiver is USuperExpression) return true
    return false
  }

  private fun checkCall(
    context: JavaContext,
    call: UCallExpression,
    superMethod: PsiMethod,
    reported: MutableSet<UCallExpression>
  ) {
    if (call in reported) return
    if (call.methodName != superMethod.name) return
    if (call.valueArgumentCount != superMethod.parameterList.parametersCount) return
    if (!isSuperCall(call)) return

    reported += call
    val message =
      "Calling an empty super method: ${superMethod.name}() is annotated @EmptySuper and should not be invoked via super"
    context.report(Incident(EMPTY_SUPER_CALL, call, context.getLocation(call), message))
  }
}