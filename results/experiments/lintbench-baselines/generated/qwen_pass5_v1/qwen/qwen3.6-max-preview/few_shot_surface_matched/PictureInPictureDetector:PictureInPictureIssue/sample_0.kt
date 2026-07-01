package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

class PictureInPictureDetector : Detector(), SourceCodeScanner {

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "PictureInPictureIssue",
      briefDescription = "Picture In Picture best practices not followed",
      explanation = """
        Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed.
        If your app does not use the new approach, your app's transition animations will be of poor quality
        compared to other apps. The new approach requires calling `setAutoEnterEnabled(true)` and
        `setSourceRectHint(...)`.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(PictureInPictureDetector::class.java, Scope.JAVA_FILE_SCOPE),
      androidSpecific = true
    )
  }

  private data class PendingReport(val context: JavaContext, val node: UCallExpression, val message: String)
  private val pendingReports = mutableListOf<PendingReport>()

  override fun getApplicableMethodNames(): List<String> = listOf("enterPictureInPictureMode")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) return
    val arg = node.valueArguments.firstOrNull() as? UCallExpression ?: return
    if (arg.methodName != "build") return

    val resolved = arg.resolve()
    if (resolved?.containingClass?.qualifiedName != "android.app.PictureInPictureParams.Builder") return

    val (hasAutoEnter, hasSourceRect) = checkBuilderChain(arg)
    if (!hasAutoEnter || !hasSourceRect) {
      val missing = mutableListOf<String>()
      if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
      if (!hasSourceRect) missing.add("setSourceRectHint(...)")
      val message = "Picture-in-Picture best practices not followed. Call ${missing.joinToString(" and ")} on the PictureInPictureParams.Builder for smoother transitions."
      pendingReports.add(PendingReport(context, node, message))
    }
  }

  private fun checkBuilderChain(buildCall: UCallExpression): Pair<Boolean, Boolean> {
    var hasAutoEnter = false
    var hasSourceRect = false
    var current: UExpression? = buildCall.receiver
    while (current != null) {
      when (current) {
        is UCallExpression -> {
          val name = current.methodName
          if (name == "setAutoEnterEnabled") {
            if (current.valueArguments.firstOrNull()?.evaluate() == true) {
              hasAutoEnter = true
            }
          } else if (name == "setSourceRectHint") {
            hasSourceRect = true
          }
          current = current.receiver
        }
        is UQualifiedReferenceExpression -> current = current.receiver
        else -> break
      }
    }
    return hasAutoEnter to hasSourceRect
  }

  override fun afterCheckEachProject(context: Context) {
    reportPending()
  }

  override fun checkPartialResults(context: Context) {
    reportPending()
  }

  private fun reportPending() {
    for (report in pendingReports) {
      val location = report.context.getLocation(report.node)
      report.context.report(Incident(ISSUE, report.node, location, report.message))
    }
    pendingReports.clear()
  }
}