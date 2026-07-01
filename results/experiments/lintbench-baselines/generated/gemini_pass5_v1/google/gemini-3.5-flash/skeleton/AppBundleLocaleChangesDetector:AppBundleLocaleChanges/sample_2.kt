package com.android.tools.lint.checks

import com.android.tools.lint.client.api.GradleScanner
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
                
                To disable language splitting in your bundle, configure your `build.gradle` file:
                ```groovy
                bundle {
                    language {
                        enableSplit = false
                    }
                }
                ```
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableReferenceNames(): List<String> = listOf("locale")

    override fun getApplicableMethodNames(): List<String> = listOf("setDefault", "setLocale", "setLocales")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (method.name == "setDefault" && evaluator.isMemberInClass(method, "java.util.Locale")) {
            reportLocaleChange(context, node)
        } else if ((method.name == "setLocale" || method.name == "setLocales") &&
            evaluator.isMemberInClass(method, "android.content.res.Configuration")
        ) {
            reportLocaleChange(context, node)
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        if (referenced is PsiField && referenced.name == "locale") {
            val containingClass = referenced.containingClass
            if (containingClass != null && containingClass.qualifiedName == "android.content.res.Configuration") {
                reportLocaleChange(context, reference)
            }
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle" && value == "false") {
            val map = context.getPartialResults(ISSUE).map(context.project)
            map.put("disableLanguageSplit", true)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // No-op
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        for (project in partialResults.projects()) {
            val map = partialResults.map(project)
            val disableLanguageSplit = map.getBoolean("disableLanguageSplit") ?: false
            if (!disableLanguageSplit) {
                val count = map.getInteger("change_count") ?: 0
                for (i in 0 until count) {
                    val locString = map.getString("change_$i") ?: continue
                    val location = locString.toLocation() ?: continue
                    context.report(
                        ISSUE,
                        location,
                        "Runtime locale changes require `bundle.language.enableSplit = false` in your build.gradle to prevent missing resources when users change languages."
                    )
                }
            }
        }
    }

    private fun reportLocaleChange(context: JavaContext, node: org.jetbrains.uast.UElement) {
        val map = context.getPartialResults(ISSUE).map(context.project)
        val count = map.getInteger("change_count") ?: 0
        val location = context.getLocation(node)
        map.put("change_$count", location.writeToString())
        map.put("change_count", count + 1)
    }

    private fun Location.writeToString(): String {
        val file = this.file.absolutePath
        val start = this.start?.offset ?: -1
        val end = this.end?.offset ?: -1
        return "$file;$start;$end"
    }

    private fun String.toLocation(): Location? {
        val parts = this.split(";")
        if (parts.size < 3) return null
        val file = java.io.File(parts[0])
        if (!file.exists()) return null
        val start = parts[1].toIntOrNull() ?: -1
        val end = parts[2].toIntOrNull() ?: -1
        if (start != -1 && end != -1) {
            try {
                val contents = file.readText()
                return Location.create(file, contents, start, end)
            } catch (e: Exception) {
                // Fallback to basic location if reading fails
            }
        }
        return Location.create(file)
    }
}