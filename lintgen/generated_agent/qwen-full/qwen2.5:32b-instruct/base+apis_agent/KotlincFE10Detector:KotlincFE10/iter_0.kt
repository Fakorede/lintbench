package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class KotlincFE10Detector : Detector(), Detector.SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "KotlincOldFrontendUsage",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val OLD_FRONTEND_METHODS = listOf("oldKotlinCompilerApi", "anotherOldMethod")
    }

    override fun getApplicableMethodNames(): List<String>? {
        return OLD_FRONTEND_METHODS
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (OLD_FRONTEND_METHODS.contains(method.name)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Avoid using old K1 Kotlin compiler APIs"
            )
        }
    }
}