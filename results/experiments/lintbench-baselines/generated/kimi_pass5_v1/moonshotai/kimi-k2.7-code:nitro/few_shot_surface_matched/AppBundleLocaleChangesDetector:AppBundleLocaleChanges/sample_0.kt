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
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App bundle may not handle runtime locale changes",
            explanation = """
                When changing locales at runtime (for example, to provide an in-app language
                switcher), the Android App Bundle must be configured to not split by locale or
                the Play Core library must be used to download additional locales at runtime.

                Either set `android.bundle.language.enableSplit = false` in your build file, or
                use `SplitInstallRequest.Builder#addLanguage(Locale)` together with
                `SplitInstallManager#startInstall(SplitInstallRequest)`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
                Scope.GRADLE_SCOPE
            ),
            androidSpecific = true,
        )

        private const val LOCALE_CLASS = "java.util.Locale"
        private const val APPCOMPAT_DELEGATE_CLASS = "androidx.appcompat.app.AppCompatDelegate"
        private const val CONFIGURATION_CLASS = "android.content.res.Configuration"
        private const val RESOURCES_CLASS = "android.content.res.Resources"
        private const val CONTEXT_CLASS = "android.content.Context"
        private const val PLAY_CORE_REQUEST_CLASS = "com.google.android.play.core.splitinstall.SplitInstallRequest"
        private const val PLAY_CORE_MANAGER_CLASS = "com.google.android.play.core.splitinstall.SplitInstallManager"

        private const val MESSAGE =
            "Runtime locale change detected. When publishing with Android App Bundle, either " +
                "disable language splits (`android.bundle.language.enableSplit = false`) or use " +
                "the Play Core library to download language splits at runtime."
    }

    private var runtimeLocaleChangeDetected = false
    private var languageSplitDisabled = false
    private var playCoreLanguageDownloadDetected = false
    private var firstLocaleChangeLocation: Location? = null

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "setDefault",
            "setApplicationLocales",
            "setLocale",
            "updateConfiguration",
            "createConfigurationContext",
            "addLanguage",
            "startInstall"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val name = method.name
        val containingClass = method.containingClass?.qualifiedName ?: return

        when (name) {
            "setDefault" -> {
                if (containingClass == LOCALE_CLASS) {
                    recordLocaleChange(context, node)
                }
            }
            "setApplicationLocales" -> {
                if (containingClass == APPCOMPAT_DELEGATE_CLASS) {
                    recordLocaleChange(context, node)
                }
            }
            "setLocale" -> {
                if (containingClass == CONFIGURATION_CLASS) {
                    recordLocaleChange(context, node)
                }
            }
            "updateConfiguration" -> {
                if (containingClass == RESOURCES_CLASS || containingClass == CONFIGURATION_CLASS) {
                    recordLocaleChange(context, node)
                }
            }
            "createConfigurationContext" -> {
                if (containingClass == CONTEXT_CLASS) {
                    recordLocaleChange(context, node)
                }
            }
            "addLanguage" -> {
                val receiverType = node.receiverType?.canonicalText
                if (receiverType?.startsWith(PLAY_CORE_REQUEST_CLASS) == true) {
                    playCoreLanguageDownloadDetected = true
                }
            }
            "startInstall" -> {
                if (containingClass == PLAY_CORE_MANAGER_CLASS) {
                    playCoreLanguageDownloadDetected = true
                }
            }
        }
    }

    private fun recordLocaleChange(context: JavaContext, node: UCallExpression) {
        if (!runtimeLocaleChangeDetected) {
            firstLocaleChangeLocation = context.getLocation(node)
        }
        runtimeLocaleChangeDetected = true
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("APPLICATION_LOCALES_CHANGED")
    }

    override fun visitReference(
        context: JavaContext,
        node: UReferenceExpression,
        referenced: PsiElement
    ) {
        if (referenced is PsiField) {
            val containingClass = referenced.containingClass?.qualifiedName
            val fieldName = referenced.name
            if (containingClass == APPCOMPAT_DELEGATE_CLASS && fieldName == "APPLICATION_LOCALES_CHANGED") {
                recordLocaleChange(context, node)
            }
        }
    }

    private fun recordLocaleChange(context: JavaContext, node: UReferenceExpression) {
        if (!runtimeLocaleChangeDetected) {
            firstLocaleChangeLocation = context.getLocation(node)
        }
        runtimeLocaleChangeDetected = true
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        propertyCookie: Any,
        valueCookie: Any,
        statementCookie: Any,
    ) {
        if (context.project.isLibrary) return
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            languageSplitDisabled = value == "false"
            if (runtimeLocaleChangeDetected && !languageSplitDisabled && !playCoreLanguageDownloadDetected) {
                val location = context.getLocation(propertyCookie)
                val incident = Incident(ISSUE, propertyCookie, location, MESSAGE)
                context.client.report(context, incident)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (context.project.isLibrary) return
        if (runtimeLocaleChangeDetected && !languageSplitDisabled && !playCoreLanguageDownloadDetected) {
            val location = firstLocaleChangeLocation ?: Location.NONE
            val incident = Incident(ISSUE, Any(), location, MESSAGE)
            context.client.report(context, incident)
        }
        resetState()
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        if (context.project.isLibrary) return
        if (runtimeLocaleChangeDetected && !languageSplitDisabled && !playCoreLanguageDownloadDetected) {
            val location = firstLocaleChangeLocation ?: Location.NONE
            val incident = Incident(ISSUE, Any(), location, MESSAGE)
            context.client.report(context, incident)
        }
        resetState()
    }

    private fun resetState() {
        runtimeLocaleChangeDetected = false
        languageSplitDisabled = false
        playCoreLanguageDownloadDetected = false
        firstLocaleChangeLocation = null
    }
}