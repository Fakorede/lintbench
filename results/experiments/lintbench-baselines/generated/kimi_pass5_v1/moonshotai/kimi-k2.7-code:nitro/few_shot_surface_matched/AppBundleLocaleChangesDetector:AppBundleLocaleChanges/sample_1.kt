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
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

  override fun getApplicableMethodNames(): List<String> =
    listOf(
      "setDefault",
      "setLocale",
      "setLocales",
      "setApplicationLocales",
      "createConfigurationContext",
      "updateConfiguration",
      "startInstall",
      "deferredInstall",
      "deferredLanguageInstall",
    )

  override fun getApplicableReferenceNames(): List<String> =
    listOf(
      "locale",
      "locales",
    )

  override fun visitMethodCall(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod,
  ) {
    val evaluator = context.evaluator
    when (method.name) {
      "setDefault" ->
        if (evaluator.isMemberInClass(method, "java.util.Locale")) {
          noteLocaleChanged(context)
        }
      "setLocale", "setLocales" ->
        if (evaluator.isMemberInSubClassOf(method, "android.content.res.Configuration")) {
          noteLocaleChanged(context)
        }
      "setApplicationLocales" ->
        if (evaluator.isMemberInClass(method, "androidx.appcompat.app.AppCompatDelegate")) {
          noteLocaleChanged(context)
        }
      "createConfigurationContext" ->
        if (evaluator.isMemberInSubClassOf(method, "android.content.Context") &&
          node.valueArguments.firstOrNull()?.getExpressionType()?.canonicalText ==
          "android.content.res.Configuration"
        ) {
          noteLocaleChanged(context)
        }
      "updateConfiguration" ->
        if (evaluator.isMemberInSubClassOf(method, "android.content.res.Resources")) {
          noteLocaleChanged(context)
        }
      "startInstall", "deferredInstall", "deferredLanguageInstall" ->
        if (evaluator.isMemberInSubClassOf(
            method,
            "com.google.android.play.core.splitinstall.SplitInstallManager",
          )
        ) {
          notePlayCoreHandled(context)
        }
    }
  }

  override fun visitReference(
    context: JavaContext,
    node: UReferenceExpression,
  ) {
    val resolved = node.resolve() as? PsiField ?: return
    val evaluator = context.evaluator
    when (resolved.name) {
      "locale", "locales" ->
        if (evaluator.isMemberInClass(resolved, "android.content.res.Configuration")) {
          noteLocaleChanged(context)
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
    statementCookie: Any,
  ) {
    if (context.project.isLibrary) return
    if (property != "enableSplit" || parent != "language" || parentParent != "bundle") {
      return
    }
    val partialResults = context.getPartialResults(ISSUE)
    if (value == "true" || value == "\"true\"") {
      partialResults.setGlobal(LANGUAGE_SPLIT_ENABLED, true)
    } else if (value == "false" || value == "\"false\"") {
      partialResults.setGlobal(LANGUAGE_SPLIT_DISABLED, true)
    }
  }

  override fun afterCheckEachProject(context: Context) {
    if (context.project.isLibrary) return
    val partialResults = context.getPartialResults(ISSUE)
    if (partialResults.getGlobal(LANGUAGE_SPLIT_ENABLED) == null &&
      partialResults.getGlobal(LANGUAGE_SPLIT_DISABLED) == null
    ) {
      partialResults.setGlobal(LANGUAGE_SPLIT_ENABLED, true)
    }
  }

  override fun checkPartialResults(context: Context, partialResult: PartialResult) {
    if (context.project.isLibrary) return
    val localeChanged = partialResult.getGlobal(LOCALE_CHANGED) == true
    val playCoreHandled = partialResult.getGlobal(PLAY_CORE_HANDLED) == true
    val splitEnabled = partialResult.getGlobal(LANGUAGE_SPLIT_ENABLED) == true
    val splitDisabled = partialResult.getGlobal(LANGUAGE_SPLIT_DISABLED) == true

    if (localeChanged && !playCoreHandled && (splitEnabled || !splitDisabled)) {
      val location = Location.create(context.project.dir)
      val message =
        "Runtime locale changes may not work correctly with Android App Bundle language splits. " +
          "Either set `bundle { language { enableSplit = false } }` or use the Play Core library " +
          "to download additional language splits at runtime."
      context.report(
        Incident(
          ISSUE,
          location,
          message,
        )
      )
    }
  }

  private fun noteLocaleChanged(context: JavaContext) {
    context.getPartialResults(ISSUE).setGlobal(LOCALE_CHANGED, true)
  }

  private fun notePlayCoreHandled(context: JavaContext) {
    context.getPartialResults(ISSUE).setGlobal(PLAY_CORE_HANDLED, true)
  }

  companion object {
    private const val LOCALE_CHANGED = "localeChanged"
    private const val PLAY_CORE_HANDLED = "playCoreHandled"
    private const val LANGUAGE_SPLIT_ENABLED = "languageSplitEnabled"
    private const val LANGUAGE_SPLIT_DISABLED = "languageSplitDisabled"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "AppBundleLocaleChanges",
        briefDescription = "App Bundle may not handle runtime locale changes",
        explanation =
          """
                    When changing locales at runtime (for example, to provide an in-app language switcher),
                    the Android App Bundle must either be configured to not split APKs by locale or the Play
                    Core library must be used to download additional language splits at runtime. Otherwise,
                    the new locale may not have the required resources available.
                """,
        moreInfo =
          "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation =
          Implementation(
            AppBundleLocaleChangesDetector::class.java,
            java.util.EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.GRADLE_SCOPE),
          ),
        androidSpecific = true,
      )
  }
}