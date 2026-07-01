package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When your app changes the user's locale at runtime (for example, to implement an
                in-app language picker) and you publish it as an Android App Bundle, you must
                either disable language splits in the base module or use the Play Core library to
                download the needed language splits at runtime. Otherwise users may see resources
                in the wrong language after switching.

                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            )
        )
    }

    private val localeChangeCalls = mutableListOf<Pair<JavaContext, UCallExpression>>()
    private var languageSplitEnabled = true
    private var usesPlayCore = false

    override fun beforeCheckRootProject(context: Context) {
        localeChangeCalls.clear()
        languageSplitEnabled = true
        usesPlayCore = false
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(
            UCallExpression::class.java,
            UReferenceExpression::class.java,
            UImportStatement::class.java
        )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (isLocaleChangeCall(node)) {
                    localeChangeCalls.add(context to node)
                }
                if (isPlayCoreElement(node.resolve())) {
                    usesPlayCore = true
                }
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                if (isPlayCoreElement(node.resolve())) {
                    usesPlayCore = true
                }
            }

            override fun visitImportStatement(node: UImportStatement) {
                val importRef = node.importReference?.asSourceString() ?: return
                if (importRef.startsWith("com.google.android.play.core")) {
                    usesPlayCore = true
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
        if (pathContains(parent, parentParent, property, "bundle", "language", "enableSplit")) {
            languageSplitEnabled = value != "false"
        }
    }

    override fun afterCheckRootProject(context: Context) {
        if (!context.project.isGradleProject) return
        if (localeChangeCalls.isEmpty() || !languageSplitEnabled || usesPlayCore) return

        val message =
            "Runtime locale changes require either `bundle.language.enableSplit = false` or the Play Core library when using Android App Bundle"

        for ((javaContext, node) in localeChangeCalls) {
            javaContext.report(ISSUE, node, javaContext.getLocation(node), message)
        }
        localeChangeCalls.clear()
    }

    private fun isLocaleChangeCall(node: UCallExpression): Boolean {
        val method = node.resolve() ?: return false
        val methodName = method.name ?: return false
        val className = method.containingClass?.qualifiedName ?: return false

        return when (className) {
            "java.util.Locale" -> methodName == "setDefault"
            "android.content.res.Resources" -> methodName == "updateConfiguration"
            "android.content.res.Configuration" -> methodName == "setLocale" || methodName == "setLayoutDirection"
            "android.content.Context" -> methodName == "createConfigurationContext"
            "androidx.appcompat.app.AppCompatDelegate" -> methodName == "setApplicationLocales"
            else -> false
        }
    }

    private fun isPlayCoreElement(element: PsiElement?): Boolean {
        if (element == null) return false
        val className = when (element) {
            is PsiClass -> element.qualifiedName
            is PsiMethod -> element.containingClass?.qualifiedName
            is PsiField -> element.containingClass?.qualifiedName
            else -> null
        } ?: return false
        return className.startsWith("com.google.android.play.core")
    }

    private fun pathContains(
        parent: String,
        parentParent: String?,
        name: String?,
        vararg segments: String
    ): Boolean {
        val path = buildString {
            if (!parentParent.isNullOrEmpty()) {
                append(parentParent).append('.')
            }
            append(parent)
            if (!name.isNullOrEmpty()) {
                append('.').append(name)
            }
        }
        return segments.all { path.contains(it, ignoreCase = true) }
    }
}