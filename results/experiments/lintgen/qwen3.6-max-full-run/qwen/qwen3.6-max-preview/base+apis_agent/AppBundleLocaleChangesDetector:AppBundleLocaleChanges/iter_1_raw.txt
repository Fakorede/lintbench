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
import com.android.tools.lint.detector.api.GradleScanner
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
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    private var hasLocaleChange = false
    private var hasPlayCore = false
    private var hasGradleConfig = false
    private val localeChangeLocations = mutableListOf<Location>()

    override fun beforeCheckEachProject(context: Context) {
        hasLocaleChange = false
        hasPlayCore = false
        hasGradleConfig = false
        localeChangeLocations.clear()
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "setLocale", "setLocales", "updateConfiguration", "setApplicationLocales", "setDefault",
        "startInstall", "requestInstall", "deferredInstall"
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val qualifiedName = method.containingClass?.qualifiedName ?: return
        val methodName = method.name

        when {
            qualifiedName == "android.content.res.Configuration" && (methodName == "setLocale" || methodName == "setLocales") -> {
                hasLocaleChange = true
                localeChangeLocations.add(context.getLocation(node))
            }
            qualifiedName == "android.content.res.Resources" && methodName == "updateConfiguration" -> {
                hasLocaleChange = true
                localeChangeLocations.add(context.getLocation(node))
            }
            qualifiedName == "androidx.appcompat.app.AppCompatDelegate" && methodName == "setApplicationLocales" -> {
                hasLocaleChange = true
                localeChangeLocations.add(context.getLocation(node))
            }
            qualifiedName == "java.util.Locale" && methodName == "setDefault" -> {
                hasLocaleChange = true
                localeChangeLocations.add(context.getLocation(node))
            }
            qualifiedName.startsWith("com.google.android.play.core.splitinstall") -> {
                hasPlayCore = true
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
        if (property == "enableSplit" && value.replace("\"", "") == "false" && parent == "language") {
            hasGradleConfig = true
        }
    }

    override fun afterCheckEachProject(context: Context) {
        if (hasLocaleChange && !hasPlayCore && !hasGradleConfig) {
            val message = "When changing locales at runtime, the App Bundle must be configured to not split by locale " +
                "(`android.bundle.language.enableSplit = false`) or the Play Core library must be used to download " +
                "additional locales at runtime."
            for (location in localeChangeLocations) {
                context.report(ISSUE, location, message)
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
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
}