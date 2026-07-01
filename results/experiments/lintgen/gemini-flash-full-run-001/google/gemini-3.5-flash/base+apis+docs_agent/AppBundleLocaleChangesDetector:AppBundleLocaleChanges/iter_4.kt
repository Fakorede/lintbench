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
import com.intellij.psi.PsiField
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UastBinaryOperator
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

    override fun visitFile(context: JavaContext) {
        val uFile = context.uastFile ?: return
        for (import in uFile.imports) {
            val importString = import.importReference?.asSourceString() ?: ""
            if (importString.contains("com.google.android.play.core.splitinstall") ||
                importString.contains("splitinstall")) {
                usesPlayCoreSplitInstall = true
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java, USimpleNameReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val name = node.methodName ?: node.methodIdentifier?.name
                if (name == "setLocale" || name == "setLocales" || name == "updateConfiguration") {
                    val method = node.resolve()
                    if (method != null) {
                        val containingClass = method.containingClass?.qualifiedName
                        if (containingClass != null && 
                            containingClass != "android.content.res.Configuration" && 
                            containingClass != "android.content.res.Resources") {
                            return
                        }
                    }
                    val location = context.getLocation(node)
                    localeChangeLocations.add(Pair(location, "Runtime locale change detected"))
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val name = node.identifier
                if (name == "locale") {
                    if (isLeftHandOfAssignment(node)) {
                        val resolved = node.resolve()
                        if (resolved is PsiField) {
                            val containingClass = resolved.containingClass?.qualifiedName
                            if (containingClass != null && containingClass != "android.content.res.Configuration") {
                                return
                            }
                        }
                        val location = context.getLocation(node)
                        localeChangeLocations.add(Pair(location, "Runtime locale change detected"))
                    }
                } else if (name == "SplitInstallManager" || name == "SplitInstallManagerFactory" || name == "SplitInstallRequest" || name.contains("splitinstall")) {
                    usesPlayCoreSplitInstall = true
                }
            }
        }
    }

    private fun isLeftHandOfAssignment(node: USimpleNameReferenceExpression): Boolean {
        var current: UElement = node
        var parent = current.uastParent
        while (parent != null) {
            if (parent is UBinaryExpression) {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    return isAncestorOf(parent.leftOperand, current)
                }
                break
            } else if (parent.javaClass.name.contains("UQualifiedExpression")) {
                current = parent
                parent = parent.uastParent
            } else {
                break
            }
        }
        return false
    }

    private fun isAncestorOf(ancestor: UElement, child: UElement): Boolean {
        var curr: UElement? = child
        while (curr != null) {
            if (curr == ancestor) return true
            curr = curr.uastParent
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