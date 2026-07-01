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
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        private const val HAS_LOCALE_CHANGE = "has_locale_change"
        private const val LOCALE_CHANGE_LOCATION_PREFIX = "locale_change_location_"
        private const val HAS_LANGUAGE_SPLIT_DISABLED = "has_language_split_disabled"
        private const val HAS_PLAY_CORE = "has_play_core"

        private const val SET_APPLICATION_LOCALES = "setApplicationLocales"
        private const val SET_DEFAULT = "setDefault"
        private const val APPLY_OVERRIDE_CONFIGURATION = "applyOverrideConfiguration"
        private const val CREATE_CONFIGURATION_CONTEXT = "createConfigurationContext"
        private const val RECREATE = "recreate"

        private const val MESSAGE = "Runtime locale changes may not work correctly with App Bundle language splits. Either disable language splits by setting `android.bundle.language.enableSplit = false` in your build.gradle file, or use the Play Core library to download additional languages at runtime."

        private const val EXPLANATION = "Apps published as Android App Bundles can generate separate APKs for each language. If the app lets the user change the locale while it is running, the current split APK may not contain the newly selected language resources. To avoid missing resources, either disable language splitting in the bundle configuration, or use the Play Core library (SplitInstallManager) to fetch the required language split at runtime. See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes."

        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableReferenceNames(): List<String>? = null

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement,
    ) {
        // No reference-only checks are needed.
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        SET_APPLICATION_LOCALES,
        SET_DEFAULT,
        APPLY_OVERRIDE_CONFIGURATION,
        CREATE_CONFIGURATION_CONTEXT,
        RECREATE,
    )

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (!isRuntimeLocaleChange(node, method)) {
            return
        }

        val partialResult = context.getPartialResult(ISSUE)
        partialResult.put(HAS_LOCALE_CHANGE, true)
        partialResult.put(
            "$LOCALE_CHANGE_LOCATION_PREFIX${context.file.name}",
            context.getLocation(node),
        )
    }

    private fun isRuntimeLocaleChange(node: UCallExpression, method: PsiMethod): Boolean {
        val name = node.methodName ?: return false
        return when (name) {
            SET_APPLICATION_LOCALES -> {
                val receiver = node.receiverType?.canonicalText
                    ?: method.containingClass?.qualifiedName
                receiver?.contains("AppCompatDelegate") == true ||
                    receiver?.contains("LocaleManager") == true
            }
            SET_DEFAULT -> method.containingClass?.qualifiedName == "java.util.Locale"
            APPLY_OVERRIDE_CONFIGURATION, CREATE_CONFIGURATION_CONTEXT -> {
                val receiver = node.receiverType?.canonicalText
                    ?: method.containingClass?.qualifiedName
                receiver?.contains("Activity") == true ||
                    receiver?.contains("ContextThemeWrapper") == true ||
                    receiver?.contains("Context") == true
            }
            RECREATE -> {
                val receiver = node.receiverType?.canonicalText
                    ?: method.containingClass?.qualifiedName
                receiver?.contains("Activity") == true
            }
            else -> false
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        valueCookie: Any,
        statementCookie: Any,
    ) {
        if (property == "enableSplit" &&
            value == "false" &&
            parent == "language" &&
            parentParent == "bundle"
        ) {
            context.getPartialResult(ISSUE).put(HAS_LANGUAGE_SPLIT_DISABLED, true)
        }
    }

    override fun checkMethodCall(
        context: GradleContext,
        statementCookie: Any,
        parent: String,
        methodName: String,
        namedArguments: List<GradleScanner.NamedValue>?,
        unnamedArguments: List<GradleScanner.NamedValue>,
        namedArgumentsCookie: Map<String, Any>,
        unnamedArgumentsCookie: List<Any>,
    ) {
        if (parent != "dependencies") {
            return
        }

        for (argument in unnamedArguments) {
            val argValue = argument.value
            if (argValue is String &&
                (argValue.contains("com.google.android.play:core") ||
                    argValue.contains("com.google.android.play:feature-delivery"))
            ) {
                context.getPartialResult(ISSUE).put(HAS_PLAY_CORE, true)
                break
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // All reporting is done in checkPartialResults().
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val map = partialResults.getAny()
        val hasLocaleChange = map[HAS_LOCALE_CHANGE] == true
        if (!hasLocaleChange) {
            return
        }

        val splitDisabled = map[HAS_LANGUAGE_SPLIT_DISABLED] == true
        val hasPlayCore = map[HAS_PLAY_CORE] == true
        if (splitDisabled || hasPlayCore) {
            return
        }

        val locations = map.entries
            .filter { it.key.startsWith(LOCALE_CHANGE_LOCATION_PREFIX) }
            .mapNotNull { it.value as? Location }

        if (locations.isEmpty()) {
            context.report(ISSUE, Location.create(context.mainProject.dir), MESSAGE)
            return
        }

        for (location in locations) {
            context.report(ISSUE, location, MESSAGE)
        }
    }
}