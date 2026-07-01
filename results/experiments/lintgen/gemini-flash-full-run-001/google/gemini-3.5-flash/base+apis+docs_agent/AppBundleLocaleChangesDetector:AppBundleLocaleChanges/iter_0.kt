package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
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

    override fun visitBuildGradle(context: GradleContext) {
        val text = context.sourceText
        val enableSplitFalse = Regex("""enableSplit\s*(=\s*|\s+)?false""").containsMatchIn(text) ||
                               Regex("""enableSplit\.set\(\s*false\s*\)""").containsMatchIn(text)
        if (enableSplitFalse) {
            languageSplitDisabled = true
        }
    }

    override fun visitFile(context: JavaContext, file: UFile) {
        val text = file.sourcePsi?.text ?: return
        if (text.contains("com.google.android.play.core.splitinstall")) {
            usesPlayCoreSplitInstall = true
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("setLocale", "setLocales", "updateConfiguration")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (evaluator.isMemberInClass(method, "android.content.res.Configuration")) {
            if (method.name == "setLocale" || method.name == "setLocales") {
                val location = context.getLocation(node)
                localeChangeLocations.add(Pair(location, "Runtime locale change detected via `Configuration.${method.name}`"))
            }
        } else if (evaluator.isMemberInClass(method, "android.content.res.Resources")) {
            if (method.name == "updateConfiguration") {
                val location = context.getLocation(node)
                localeChangeLocations.add(Pair(location, "Runtime locale change detected via `Resources.updateConfiguration`"))
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UBinaryExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitBinaryExpression(node: UBinaryExpression) {
                if (node.operator is UastBinaryOperator.ASSIGN) {
                    val left = node.leftOperand
                    if (left is UReferenceExpression) {
                        val resolved = left.resolve()
                        if (resolved is PsiField && resolved.name == "locale") {
                            val containingClass = resolved.containingClass
                            if (containingClass?.qualifiedName == "android.content.res.Configuration") {
                                val location = context.getLocation(node)
                                localeChangeLocations.add(Pair(location, "Runtime locale change detected via `Configuration.locale` assignment"))
                            }
                        }
                    }
                }
            }
        }
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