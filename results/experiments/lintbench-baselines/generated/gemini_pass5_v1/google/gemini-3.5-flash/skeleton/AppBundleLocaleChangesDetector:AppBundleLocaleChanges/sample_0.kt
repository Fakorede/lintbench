package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Incident
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiField
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UBinaryExpression
import java.io.File

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
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableReferenceNames(): List<String>? = listOf("locale")

    override fun getApplicableMethodNames(): List<String>? = listOf("setDefault", "setLocale", "setLocales")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (method.name == "setDefault" && evaluator.isMemberInClass(method, "java.util.Locale")) {
            reportLocaleChange(context, node, "Locale.setDefault")
        } else if ((method.name == "setLocale" || method.name == "setLocales") &&
            evaluator.isMemberInClass(method, "android.content.res.Configuration")) {
            reportLocaleChange(context, node, "Configuration.${method.name}")
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        if (reference.resolvedName == "locale") {
            if (isWriteToLocale(reference)) {
                val evaluator = context.evaluator
                val psiField = referenced as? PsiField
                if (psiField != null && evaluator.isMemberInClass(psiField, "android.content.res.Configuration")) {
                    reportLocaleChange(context, reference, "Configuration.locale")
                }
            }
        }
    }

    private fun isWriteToLocale(reference: UReferenceExpression): Boolean {
        val parent = reference.uastParent
        if (parent is UBinaryExpression) {
            if (parent.leftOperand == reference) {
                val opText = parent.operator.text
                if (opText == "=") {
                    return true
                }
            }
        }
        return false
    }

    private fun reportLocaleChange(context: JavaContext, node: org.jetbrains.uast.UElement, apiName: String) {
        val location = context.getLocation(node)
        val file = location.file
        val start = location.start?.offset ?: -1
        val end = location.end?.offset ?: -1
        if (start != -1 && end != -1) {
            val partialResults = context.getPartialResults(ISSUE)
            val map = partialResults.map
            val count = map.getInt("count") ?: 0
            val prefix = "occ_$count"
            map.put("${prefix}_file", file.absolutePath)
            map.put("${prefix}_start", start)
            map.put("${prefix}_end", end)
            map.put("${prefix}_api", apiName)
            map.put("count", count + 1)
        }
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            if (value == "false") {
                val partialResults = context.getPartialResults(ISSUE)
                partialResults.map.put("enableSplitFalse", true)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // No action required after each project check
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        for (project in partialResults.projects()) {
            val map = partialResults.get(project)
            val enableSplitFalse = map.getBoolean("enableSplitFalse") ?: false
            if (!enableSplitFalse) {
                val count = map.getInt("count") ?: 0
                for (i in 0 until count) {
                    val filePath = map.getString("occ_${i}_file") ?: continue
                    val start = map.getInt("occ_${i}_start") ?: -1
                    val end = map.getInt("occ_${i}_end") ?: -1
                    val api = map.getString("occ_${i}_api") ?: "locale change"
                    
                    val file = File(filePath)
                    if (file.exists()) {
                        val contents = file.readText()
                        val location = Location.create(file, contents, start, end)
                        context.report(
                            Incident(
                                ISSUE,
                                location,
                                "Runtime locale changes with $api require bundle.language.enableSplit = false in build.gradle"
                            )
                        )
                    }
                }
            }
        }
    }
}