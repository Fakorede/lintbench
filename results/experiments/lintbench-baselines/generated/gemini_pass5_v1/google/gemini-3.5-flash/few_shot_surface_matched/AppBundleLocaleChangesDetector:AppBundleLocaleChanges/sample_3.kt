package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResults
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private val projectIncidents = mutableMapOf<com.android.tools.lint.detector.api.Project, MutableList<Incident>>()
    private val projectSplitDisabled = mutableMapOf<com.android.tools.lint.detector.api.Project, Boolean>()

    override fun getApplicableMethodNames(): List<String> = listOf(
        "setApplicationLocales",
        "updateConfiguration",
        "createConfigurationContext",
        "setDefault",
        "setLocale",
        "setLocales"
    )

    override fun getApplicableReferenceNames(): List<String> = listOf(
        "locale"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val name = method.name
        val isLocaleChange = when (name) {
            "setApplicationLocales" -> isExpectedClass(context, method, "androidx.appcompat.app.AppCompatDelegate")
            "updateConfiguration" -> isExpectedClass(context, method, "android.content.res.Resources")
            "createConfigurationContext" -> isExpectedClass(context, method, "android.content.Context")
            "setDefault" -> isExpectedClass(context, method, "java.util.Locale")
            "setLocale", "setLocales" -> isExpectedClass(context, method, "android.content.res.Configuration")
            else -> false
        }
        if (isLocaleChange) {
            reportLocaleChange(context, node)
        }
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiField && referenced.name == "locale") {
            val containingClass = referenced.containingClass
            if (containingClass != null && context.evaluator.isSubclassOf(containingClass, "android.content.res.Configuration", false)) {
                reportLocaleChange(context, reference)
            }
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any
    ) {
        if (property == "enableSplit" && value == "false" && parent == "language" && parentParent == "bundle") {
            projectSplitDisabled[context.project] = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.project.isLibrary) return
        val disabled = projectSplitDisabled[context.project] ?: false
        if (!disabled) {
            val incidents = projectIncidents[context.project]
            if (incidents != null) {
                for (incident in incidents) {
                    context.report(incident)
                }
            }
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResults) {
        afterCheckEachProject(context)
    }

    private fun isExpectedClass(context: JavaContext, method: PsiMethod, className: String): Boolean {
        return context.evaluator.isMemberInSubClassOf(method, className)
    }

    private fun reportLocaleChange(context: JavaContext, element: UElement) {
        val incident = Incident(
            ISSUE,
            element,
            context.getLocation(element),
            "When changing locales at runtime, the Android App Bundle must be configured to not split by " +
                "locale or the Play Core library must be used to download additional locales."
        )
        projectIncidents.getOrPut(context.project) { mutableListOf() }.add(incident)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = "When changing locales at runtime (e.g. to provide an in-app language switcher), " +
                "the Android App Bundle must be configured to not split by locale or the Play Core " +
                "library must be used to download additional locales at runtime.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_AND_GRADLE_FILES
            ),
            androidSpecific = true
        )
    }
}