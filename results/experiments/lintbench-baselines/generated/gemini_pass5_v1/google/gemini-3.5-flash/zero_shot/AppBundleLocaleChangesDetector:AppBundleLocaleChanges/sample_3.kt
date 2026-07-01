package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import java.util.EnumSet

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "AppBundleLocaleChanges",
            briefDescription = "App Bundle handling of runtime locale changes",
            explanation = """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
                
                See https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes \
                for more details.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                AppBundleLocaleChangesDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java, UBinaryExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val evaluator = context.evaluator

                if (evaluator.isMemberInClass(method, "java.util.Locale") && method.name == "setDefault") {
                    reportLocaleChange(context, node)
                    return
                }

                if (evaluator.isMemberInClass(method, "android.os.LocaleList") && method.name == "setDefault") {
                    reportLocaleChange(context, node)
                    return
                }

                if (evaluator.isMemberInClass(method, "android.content.res.Configuration")) {
                    if (method.name == "setLocale" || method.name == "setLocales") {
                        reportLocaleChange(context, node)
                        return
                    }
                }

                if (evaluator.isMemberInSubClassOf(method, "android.content.Context", false) &&
                    method.name == "createConfigurationContext"
                ) {
                    reportLocaleChange(context, node)
                    return
                }
            }

            override fun visitBinaryExpression(node: UBinaryExpression) {
                if (node.operator != UastBinaryOperator.ASSIGN) return
                val leftReference = node.leftOperand as? UReferenceExpression ?: return
                val resolvedField = leftReference.resolve() as? PsiField ?: return
                val containingClass = resolvedField.containingClass ?: return

                if (containingClass.qualifiedName == "android.content.res.Configuration" && resolvedField.name == "locale") {
                    reportLocaleChange(context, node)
                }
            }
        }
    }

    private fun reportLocaleChange(context: JavaContext, node: UElement) {
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Found runtime locale change. Please ensure that the Android App Bundle is configured " +
                    "to not split by locale, or that the Play Core library is used to download additional locales."
        )
    }
}