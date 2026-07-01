package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node
import java.io.File

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "setDefault",
            "setLocale",
            "setLocales",
            "createConfigurationContext",
            "setApplicationLocales",
            "updateConfiguration"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val containingClass = method.containingClass

        var isLocaleChange = when (method.name) {
            "setDefault" -> evaluator.isMemberInClass(method, "java.util.Locale") || evaluator.isMemberInClass(method, "android.os.LocaleList")
            "setLocale", "setLocales" -> evaluator.isMemberInClass(method, "android.content.res.Configuration")
            "createConfigurationContext" -> evaluator.isMemberInSubclassOf(method, "android.content.Context", false)
            "updateConfiguration" -> evaluator.isMemberInClass(method, "android.content.res.Resources")
            "setApplicationLocales" -> evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate") || evaluator.isMemberInClass(method, "android.app.LocaleManager")
            else -> false
        }

        if (!isLocaleChange) {
            val callText = node.sourcePsi?.text ?: ""
            val fallback = when (method.name) {
                "setDefault" -> callText.contains("Locale") || callText.contains("LocaleList")
                "setLocale", "setLocales" -> containingClass == null || containingClass.name == "Configuration"
                "createConfigurationContext" -> containingClass == null || containingClass.name == "Context"
                "setApplicationLocales" -> callText.contains("AppCompatDelegate") || callText.contains("LocaleManager")
                "updateConfiguration" -> containingClass == null || containingClass.name == "Resources"
                else -> false
            }
            if (!fallback) return
        }

        if (isPlayCoreUsed(context) || isLanguageSplitDisabled(context)) {
            return
        }

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Found custom locale formatting/modification, but language splits are not disabled"
        )
    }

    private fun isPlayCoreUsed(context: JavaContext): Boolean {
        if (context.evaluator.findClass("com.google.android.play.core.splitinstall.SplitInstallManager") != null) {
            return true
        }
        val project = context.project
        if (project.dependsOn("com.google.android.play:core") == true ||
            project.dependsOn("com.google.android.play:feature-delivery") == true ||
            project.dependsOn("com.google.android.play:core-ktx") == true) {
            return true
        }
        return false
    }

    private fun isLanguageSplitDisabled(context: JavaContext): Boolean {
        try {
            val gradleProject = context.project.gradleProject
            if (gradleProject != null) {
                val bundle = gradleProject.bundleOptions
                if (bundle != null) {
                    val language = bundle.language
                    if (language != null) {
                        val enable = language.enable
                        if (enable != null && !enable) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // Ignore
        }

        try {
            for (file in context.project.gradleBuildScripts) {
                if (file.exists()) {
                    val text = file.readText()
                    if (text.contains("enableSplit")) {
                        val regex = Regex("""enableSplit\s*(=\s*|\.\s*set\s*\(\s*|\s+)false""")
                        if (regex.containsMatchIn(text)) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // Ignore
        }

        return false
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
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}