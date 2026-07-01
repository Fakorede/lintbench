package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Scope
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UastBinaryOperator
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private var languageSplitDisabled = false
    private var usesPlayCoreSplitInstall = false
    private val localeChangeLocations = mutableListOf<Pair<Location, String>>()

    override fun beforeCheckEachProject(context: Context) {
        languageSplitDisabled = false
        usesPlayCoreSplitInstall = false
        localeChangeLocations.clear()
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
        if (property == "enableSplit" && value == "false" && parent == "language") {
            languageSplitDisabled = true
        }
    }

    override fun checkMethodCall(
        context: GradleContext,
        statement: String,
        parent: String?,
        parentParent: String?,
        namedArguments: Map<String, String>,
        unnamedArguments: List<String>,
        cookie: Any
    ) {
        if (parent == "dependencies") {
            for (arg in unnamedArguments) {
                if (arg.contains("com.google.android.play:core") || 
                    arg.contains("com.google.android.play:feature-delivery")) {
                    usesPlayCoreSplitInstall = true
                }
            }
        } else if (statement == "enableSplit" && unnamedArguments.contains("false") && parent == "language") {
            languageSplitDisabled = true
        } else if (statement == "set" && parent == "enableSplit" && unnamedArguments.contains("false")) {
            languageSplitDisabled = true
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("setLocale", "setLocales", "updateConfiguration")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(method, "android.content.res.Configuration")) {
            if (method.name == "setLocale" || method.name == "setLocales") {
                val location = context.getLocation(node)
                localeChangeLocations.add(Pair(location, "Runtime locale change detected via `Configuration.${method.name}`"))
            }
        } else if (evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            if (method.name == "updateConfiguration") {
                val location = context.getLocation(node)
                localeChangeLocations.add(Pair(location, "Runtime locale change detected via `Resources.updateConfiguration`"))
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("SplitInstallManager", "SplitInstallManagerFactory", "SplitInstallRequest", "locale")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        if (referenced is PsiField && referenced.name == "locale") {
            val containingClass = referenced.containingClass
            if (containingClass?.qualifiedName == "android.content.res.Configuration") {
                if (isLeftHandOfAssignment(reference)) {
                    val location = context.getLocation(reference)
                    localeChangeLocations.add(Pair(location, "Runtime locale change detected via `Configuration.locale` assignment"))
                }
            }
        } else if (referenced is PsiClass) {
            val fqName = referenced.qualifiedName
            if (fqName != null && fqName.startsWith("com.google.android.play.core.splitinstall")) {
                usesPlayCoreSplitInstall = true
            }
        }
    }

    private fun isLeftHandOfAssignment(node: UReferenceExpression): Boolean {
        val parent = node.uastParent
        if (parent is UBinaryExpression) {
            if (parent.leftOperand == node) {
                val op = parent.operator
                return op == UastBinaryOperator.ASSIGN
            }
        }
        return false
    }

    override fun afterCheckEachProject(context: Context) {
        if (!languageSplitDisabled && !usesPlayCoreSplitInstall) {
            for ((location, _) in localeChangeLocations) {
                context.report(
                    ISSUE,
                    location,
                    "When changing locales at runtime, the Android App Bundle must be configured to not split by locale or the Play Core library must be used to download additional locales."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            )
        )
    }
}