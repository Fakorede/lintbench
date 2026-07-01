package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node
import java.io.File
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    private val pendingReports = mutableListOf<PendingReport>()
    private var usesPlayCoreSplitInstall = false

    private class PendingReport(
        val context: JavaContext,
        val location: Location
    )

    override fun beforeCheckEachProject(context: Context) {
        pendingReports.clear()
        usesPlayCoreSplitInstall = false
    }

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
        val qualifiedName = containingClass?.qualifiedName

        var isTarget = when (method.name) {
            "setDefault" -> qualifiedName == "java.util.Locale" || qualifiedName == "android.os.LocaleList" || evaluator.isMemberInClass(method, "java.util.Locale") || evaluator.isMemberInClass(method, "android.os.LocaleList")
            "setLocale", "setLocales" -> qualifiedName == "android.content.res.Configuration" || evaluator.isMemberInClass(method, "android.content.res.Configuration")
            "createConfigurationContext" -> qualifiedName == "android.content.Context" || evaluator.inheritsFrom(containingClass, "android.content.Context", false)
            "setApplicationLocales" -> qualifiedName == "androidx.appcompat.app.AppCompatDelegate" || evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate")
            "updateConfiguration" -> qualifiedName == "android.content.res.Resources" || evaluator.isMemberInClass(method, "android.content.res.Resources")
            else -> false
        }

        if (!isTarget && containingClass == null) {
            val callText = node.sourcePsi?.text ?: ""
            isTarget = when (method.name) {
                "setDefault" -> callText.contains("Locale") || callText.contains("LocaleList")
                "setLocale", "setLocales" -> true
                "createConfigurationContext" -> true
                "setApplicationLocales" -> callText.contains("AppCompatDelegate")
                "updateConfiguration" -> true
                else -> false
            }
        }

        if (isTarget) {
            pendingReports.add(PendingReport(context, context.getLocation(node)))
        }
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("SplitInstallManager", "SplitInstallRequest", "SplitInstallManagerFactory")
    }

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        usesPlayCoreSplitInstall = true
    }

    override fun afterCheckEachProject(context: Context) {
        if (pendingReports.isEmpty()) {
            return
        }

        var languageSplitDisabled = false
        val projectDir = context.project.dir
        val gradleFiles = mutableListOf<File>()

        fun collectGradleFiles(dir: File) {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    if (file.name != "build" && file.name != ".gradle" && file.name != ".idea") {
                        collectGradleFiles(file)
                    }
                } else if (file.name == "build.gradle" || file.name == "build.gradle.kts") {
                    gradleFiles.add(file)
                }
            }
        }
        collectGradleFiles(projectDir)

        val blockRegex = Regex("""language\s*\{\s*[^}]*enableSplit\s*(=\s*|\.\s*set\s*\(\s*|\s+)false""")
        val flatRegex = Regex("""bundle\s*\.\s*language\s*\.\s*enableSplit\s*(=\s*|\.\s*set\s*\(\s*|\s+)false""")

        for (file in gradleFiles) {
            try {
                val text = file.readText()
                if (blockRegex.containsMatchIn(text) || flatRegex.containsMatchIn(text)) {
                    languageSplitDisabled = true
                    break
                }
            } catch (e: Exception) {
                // Ignore read errors
            }
        }

        if (!languageSplitDisabled && !usesPlayCoreSplitInstall) {
            for (report in pendingReports) {
                report.context.report(
                    ISSUE,
                    report.location,
                    "Runtime locale changes require disabling language splits in bundle configuration or using Play Core SplitInstallManager"
                )
            }
        }
        pendingReports.clear()
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