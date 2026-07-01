package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        private const val KEY_PENDING_WARNINGS = "AppBundleLocaleChangesDetector.pendingWarnings"
        private const val KEY_SPLIT_DISABLED = "AppBundleLocaleChangesDetector.splitDisabled"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            )
        )
    }

    private data class PendingWarning(
        val context: JavaContext,
        val location: Location
    )

    @Suppress("UNCHECKED_CAST")
    private fun getPendingWarnings(project: Project): MutableList<PendingWarning> {
        var list = project.getProperty(KEY_PENDING_WARNINGS) as? MutableList<PendingWarning>
        if (list == null) {
            list = mutableListOf()
            project.putProperty(KEY_PENDING_WARNINGS, list)
        }
        return list
    }

    private fun addPendingWarning(context: JavaContext, location: Location) {
        val mainProject = context.mainProject
        val list = getPendingWarnings(mainProject)
        list.add(PendingWarning(context, location))
    }

    override fun getApplicableMethodNames(): List<String> = listOf("setLocale", "setLocales", "updateConfiguration")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(method, "android.content.res.Configuration") ||
            evaluator.isMemberInClass(method, "android.content.res.Resources")
        ) {
            val location = context.getLocation(node)
            addPendingWarning(context, location)
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf("locale", "locales")

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiField) {
            val evaluator = context.evaluator
            if (evaluator.isMemberInClass(referenced, "android.content.res.Configuration")) {
                if (isAssignmentLHS(reference)) {
                    val location = context.getLocation(reference)
                    addPendingWarning(context, location)
                }
            }
        }
    }

    private fun isAssignmentLHS(expression: UExpression): Boolean {
        val parent = expression.uastParent
        if (parent is UBinaryExpression) {
            return parent.leftOperand == expression && parent.operator.text == "="
        }
        return false
    }

    override fun visitBuildGradle(context: GradleContext) {
        val text = context.source
        if (text.contains("enableSplit") && text.contains("false")) {
            if (text.contains("language") || text.contains("bundle")) {
                context.mainProject.putProperty(KEY_SPLIT_DISABLED, true)
            }
        }
    }

    override fun afterCheckProject(context: Context) {
        if (context.project != context.mainProject) {
            return
        }
        val mainProject = context.mainProject
        val disabled = mainProject.getProperty(KEY_SPLIT_DISABLED) == true
        if (!disabled) {
            val list = mainProject.getProperty(KEY_PENDING_WARNINGS) as? List<PendingWarning>
            if (list != null) {
                for (warning in list) {
                    warning.context.report(
                        ISSUE,
                        warning.location,
                        "When changing locales at runtime, the Android App Bundle must be configured to not split by locale or the Play Core library must be used to download additional locales at runtime."
                    )
                }
            }
        }
        mainProject.putProperty(KEY_PENDING_WARNINGS, null)
        mainProject.putProperty(KEY_SPLIT_DISABLED, null)
    }
}