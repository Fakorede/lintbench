package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            CipherGetInstanceDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "DeprecatedProvider",
            briefDescription = "Using BC Provider",
            explanation = "The BC provider has been deprecated and will not be provided when targetSdkVersion is P or higher. " +
                "See https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("getInstance")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = context.evaluator.getQualifiedName(containingClass) ?: return
        if (!qualifiedName.startsWith("java.security.") &&
            !qualifiedName.startsWith("javax.crypto.") &&
            !qualifiedName.startsWith("javax.net.ssl.")) {
            return
        }

        for (arg in node.valueArguments) {
            if (context.evaluator.getString(arg) == "BC") {
                context.report(
                    ISSUE,
                    arg,
                    context.getLocation(arg),
                    "Using BC Provider"
                )
                return
            }
        }
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        return context.project.targetSdkVersion >= 28
    }
}