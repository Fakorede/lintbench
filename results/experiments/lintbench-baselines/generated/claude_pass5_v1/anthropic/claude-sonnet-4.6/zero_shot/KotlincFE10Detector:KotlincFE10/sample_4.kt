package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UImportStatement

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.psi",
            "org.jetbrains.kotlin.cfg",
            "org.jetbrains.kotlin.checkers",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.context",
            "org.jetbrains.kotlin.contracts",
            "org.jetbrains.kotlin.coroutines",
            "org.jetbrains.kotlin.di",
            "org.jetbrains.kotlin.extensions",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.js.resolve",
            "org.jetbrains.kotlin.jvm.resolve",
            "org.jetbrains.kotlin.kapt",
            "org.jetbrains.kotlin.modules",
            "org.jetbrains.kotlin.parsing",
            "org.jetbrains.kotlin.platform",
            "org.jetbrains.kotlin.resolve.calls",
            "org.jetbrains.kotlin.resolve.constants",
            "org.jetbrains.kotlin.resolve.deprecation",
            "org.jetbrains.kotlin.resolve.extensions",
            "org.jetbrains.kotlin.resolve.jvm",
            "org.jetbrains.kotlin.resolve.lazy",
            "org.jetbrains.kotlin.resolve.scopes",
            "org.jetbrains.kotlin.resolve.source",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.storage",
            "org.jetbrains.kotlin.synthetic"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Usage of old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes() = listOf(UImportStatement::class.java)

    override fun visitImportStatement(context: JavaContext, node: UImportStatement) {
        val importReference = node.importReference ?: return
        val importString = importReference.asSourceString()
        for (pkg in FE10_PACKAGES) {
            if (importString.startsWith(pkg)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using old K1 Kotlin compiler APIs from `$pkg`. " +
                        "K2 (new Kotlin compiler frontend) is coming; prefer K2-compatible APIs."
                )
                return
            }
        }
    }

    override fun getApplicableMethodNames(): List<String>? = null

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val containingClass = method.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        for (pkg in FE10_PACKAGES) {
            if (qualifiedName.startsWith(pkg)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using old K1 Kotlin compiler APIs from `$pkg`. " +
                        "K2 (new Kotlin compiler frontend) is coming; prefer K2-compatible APIs."
                )
                return
            }
        }
    }
}