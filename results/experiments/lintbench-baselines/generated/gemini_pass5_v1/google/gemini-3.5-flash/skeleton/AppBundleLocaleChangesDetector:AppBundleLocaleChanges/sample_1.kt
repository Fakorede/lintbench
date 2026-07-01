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
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
        )

        private const val KEY_ENABLE_SPLIT_DISABLED = "enableSplitDisabled"
        private const val KEY_PLAY_CORE_USED = "playCoreUsed"
        private const val KEY_LOCALE_CHANGES = "localeChanges"

        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
                
                By default, App Bundles split resources by locale, which means users only download \
                the resources matching their system language. If you change the locale at runtime \
                to a language the user didn't have at install time, the app will miss resources \
                and might fall back to default languages or crash.
                
                To fix this, either disable language splitting in your `build.gradle` file:
                ```groovy
                bundle {
                    language {
                        enableSplit = false
                    }
                }
                ```
                Or use the Play Core API to dynamically download the required languages.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableReferenceNames(): List<String> {
        return listOf("locale")
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("setDefault", "setLocale", "setLocales", "addLanguage")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val evaluator = context.evaluator
        if (method.name == "setDefault" && evaluator.isMemberInClass(method, "java.util.Locale")) {
            recordLocaleChange(context, node)
        } else if ((method.name == "setLocale" || method.name == "setLocales") &&
            evaluator.isMemberInClass(method, "android.content.res.Configuration")) {
            recordLocaleChange(context, node)
        } else if (method.name == "addLanguage") {
            val containingClass = method.containingClass?.qualifiedName
            if (containingClass == "com.google.android.play.core.splitinstall.SplitInstallRequest.Builder" ||
                containingClass?.contains("SplitInstallRequest") == true) {
                recordPlayCoreUsage(context)
            }
        }
    }

    override fun visitReference(
        context: JavaContext, reference: UReferenceExpression, referenced: PsiElement,
    ) {
        val evaluator = context.evaluator
        if (reference.resolvedName == "locale") {
            val isConfigField = (referenced is PsiField &&
                evaluator.isMemberInClass(referenced, "android.content.res.Configuration"))
            if (isConfigField && isWriteAccess(reference)) {
                recordLocaleChange(context, reference)
            }
        }
    }

    private fun isWriteAccess(node: UReferenceExpression): Boolean {
        val parent = node.uastParent
        if (parent is UBinaryExpression) {
            return parent.operator == UastBinaryOperator.ASSIGN && parent.leftOperand == node
        }
        return false
    }

    private fun recordLocaleChange(context: JavaContext, node: UElement) {
        val map = context.getPartialResults(ISSUE).map
        val currentChanges = map.getString(KEY_LOCALE_CHANGES)
        val location = context.getLocation(node)
        val file = location.file.absolutePath
        val start = location.start?.offset ?: -1
        val end = location.end?.offset ?: -1
        val encoded = "$file:$start:$end"
        
        val newChanges = if (currentChanges.isNullOrEmpty()) {
            encoded
        } else {
            "$currentChanges;$encoded"
        }
        map.put(KEY_LOCALE_CHANGES, newChanges)
    }

    private fun recordPlayCoreUsage(context: JavaContext) {
        val map = context.getPartialResults(ISSUE).map
        map.put(KEY_PLAY_CORE_USED, true)
    }

    override fun checkDslPropertyAssignment(
        context: GradleContext, property: String, value: String,
        parent: String, parentParent: String?,
        valueCookie: Any, statementCookie: Any,
    ) {
        if (property == "enableSplit" && parent == "language" && parentParent == "bundle") {
            val disabled = value.trim().removeSurrounding("\"").removeSurrounding("'") == "false"
            val map = context.getPartialResults(ISSUE).map
            map.put(KEY_ENABLE_SPLIT_DISABLED, disabled)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        // Handled globally in checkPartialResults
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        for (project in partialResults.projects()) {
            val map = partialResults.get(project)
            val localeChangesStr = map.getString(KEY_LOCALE_CHANGES)
            if (localeChangesStr.isNullOrEmpty()) {
                continue
            }
            
            val enableSplitDisabled = map.getBoolean(KEY_ENABLE_SPLIT_DISABLED) ?: false
            val playCoreUsed = map.getBoolean(KEY_PLAY_CORE_USED) ?: false
            
            if (!enableSplitDisabled && !playCoreUsed) {
                val encodedLocations = localeChangesStr.split(';')
                for (encoded in encodedLocations) {
                    if (encoded.isEmpty()) continue
                    val parts = encoded.split(':')
                    if (parts.size < 3) continue
                    val filePath = parts[0]
                    val startOffset = parts[1].toIntOrNull() ?: -1
                    val endOffset = parts[2].toIntOrNull() ?: -1
                    
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        val contents = try { file.readText() } catch (e: Exception) { null }
                        val location = if (contents != null && startOffset in 0..contents.length && endOffset in startOffset..contents.length) {
                            Location.create(file, contents, startOffset, endOffset)
                        } else {
                            Location.create(file)
                        }
                        
                        context.report(
                            ISSUE,
                            location,
                            "When changing locales at runtime, the Android App Bundle must be configured " +
                            "to not split by locale (`bundle.language.enableSplit = false`) or the Play Core " +
                            "library must be used to download additional locales."
                        )
                    }
                }
            }
        }
    }
}