package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), Detector.UastScanner, Detector.GradleScanner {

    companion object {
        private const val KEY_SPLIT_DISABLED = "app_bundle_locale_split_disabled"
        private const val KEY_PLAY_CORE = "app_bundle_play_core_present"

        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.

                To fix this, either disable locale splits in your build.gradle:
                android {
                    bundle {
                        language {
                            enableSplit = false
                        }
                    }
                }
                Or use the Play Core library to dynamically download locale resources.

                Reference: https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE)
            )
        )
    }

    override fun visitDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: Any?,
        parent: String?
    ) {
        if (property == "enableSplit" && (value == false || value.toString() == "false")) {
            context.project.putClientProperty(KEY_SPLIT_DISABLED, true)
        }
    }

    override fun visitDependency(context: GradleContext, dependency: DependencyModel) {
        if (dependency.group == "com.google.android.play" &&
            (dependency.name == "core" || dependency.name == "core-ktx" || dependency.name == "feature-delivery")
        ) {
            context.project.putClientProperty(KEY_PLAY_CORE, true)
        }
    }

    override fun getApplicableMethodNames(): List<String> =
        listOf("setDefault", "setLocale", "setApplicationLocales")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        method.containingClass?.qualifiedName ?: return

        val isLocaleChange = when (method.name) {
            "setDefault" -> evaluator.isMemberInClass(method, "java.util.Locale")
            "setLocale" -> evaluator.isMemberInClass(method, "android.content.res.Configuration")
            "setApplicationLocales" -> evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate") ||
                                        evaluator.isMemberInClass(method, "android.app.LocaleManager")
            else -> false
        }

        if (isLocaleChange) {
            val splitDisabled = context.project.getClientProperty<Boolean>(KEY_SPLIT_DISABLED) == true
            val playCorePresent = context.project.getClientProperty<Boolean>(KEY_PLAY_CORE) == true

            if (!splitDisabled && !playCorePresent) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Runtime locale changes require disabling locale splits in the App Bundle configuration " +
                    "(android.bundle.language.enableSplit = false) or using the Play Core library to download locales dynamically."
                )
            }
        }
    }
}