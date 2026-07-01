package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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
        private const val KEY_LOCALE_CHANGE = "localeChange"
        private const val KEY_PLAY_CORE = "playCore"
        private const val KEY_LANGUAGE_SPLIT_DISABLED = "languageSplitDisabled"

        private val LOCALE_CHANGE_METHODS = listOf(
            "setDefault", "setLocale", "setLocales",
            "updateConfiguration", "setApplicationLocales"
        )

        private val PLAY_CORE_REFERENCES = listOf(
            "SplitInstallManager",
            "SplitInstallRequest",
            "SplitInstallManagerFactory",
            "SplitInstallSessionState",
            "SplitInstallStateUpdatedListener"
        )

        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When an app changes the user's locale at runtime (for example, with an
                in-app language picker), resources for the new locale may not be installed
                if the app is distributed as an Android App Bundle with language splits
                enabled. To avoid missing resources, either disable language splits in the
                bundle configuration (`android.bundle.language.enableSplit = false`) or
                use the Play Core Split Install API to download the required language split.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableReferenceNames(): List<String>? = PLAY_CORE_REFERENCES

    override fun getApplicableMethodNames(): List<String>? = LOCALE_CHANGE_METHODS

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val name = method.name ?: return
        val cls = method.containingClass?.qualifiedName ?: return
        val isLocaleChange = when (name) {
            "setDefault" -> cls == "java.util.Locale"
            "setLocale", "setLocales" -> cls == "android.content.res.Configuration"
            "updateConfiguration" -> cls == "android.content.res.Resources"
            "setApplicationLocales" -> cls.endsWith("AppCompatDelegate")
            else -> false
        }
        if (isLocaleChange) {
            context.getPartialResults(ISSUE).map.setBoolean(KEY_LOCALE_CHANGE, true)
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        val name = reference.resolvedName ?: return
        if (name in PLAY_CORE_REFERENCES) {
            context.getPartialResults(ISSUE).map.setBoolean(KEY_PLAY_CORE, true)
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (parent == "language" && parentParent == "bundle" && property == "enableSplit") {
            if (value.trim().equals("false", ignoreCase = true)) {
                context.getPartialResults(ISSUE).map.setBoolean(KEY_LANGUAGE_SPLIT_DISABLED, true)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // Cross-project aggregation and reporting is done in checkPartialResults.
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        var hasLocaleChange = false
        var hasSplitDisabled = false
        var hasPlayCore = false
        for (project in context.mainProject.allProjects) {
            val map = partialResults.getProject(project)?.map ?: continue
            if (map.getBoolean(KEY_LOCALE_CHANGE, false) == true) {
                hasLocaleChange = true
            }
            if (map.getBoolean(KEY_LANGUAGE_SPLIT_DISABLED, false) == true) {
                hasSplitDisabled = true
            }
            if (map.getBoolean(KEY_PLAY_CORE, false) == true) {
                hasPlayCore = true
            }
        }
        if (hasLocaleChange && !hasSplitDisabled && !hasPlayCore) {
            context.report(
                ISSUE,
                Location.create(context.mainProject.dir),
                "Runtime locale changes require `android.bundle.language.enableSplit = false` or use of the Play Core Split Install API."
            )
        }
    }
}