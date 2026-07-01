package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMember
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of the Kotlin compiler, which encompasses the new \
                frontend, is coming. Avoid using internal APIs from the old K1 frontend \
                (FE1.0) so that code remains compatible with the K2 compiler.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val OLD_FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.psi2ir.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.context.",
            "org.jetbrains.kotlin.analyzer.",
            "org.jetbrains.kotlin.storage.",
            "org.jetbrains.kotlin.incremental.components."
        )

        private val OLD_FE10_CLASSES = setOf(
            "org.jetbrains.kotlin.analyzer.AnalysisResult",
            "org.jetbrains.kotlin.container.ComponentProvider",
            "org.jetbrains.kotlin.resolve.BindingContext",
            "org.jetbrains.kotlin.resolve.BindingTrace",
            "org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer",
            "org.jetbrains.kotlin.resolve.TopDownAnalysisContext",
            "org.jetbrains.kotlin.resolve.LazyTopDownAnalyzerForTopLevel"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(
            UReferenceExpression::class.java,
            UTypeReferenceExpression::class.java,
            UCallExpression::class.java,
            UImportStatement::class.java
        )

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {

            override fun visitReferenceExpression(node: UReferenceExpression) {
                val target = node.resolve()
                val qualifiedName = when (target) {
                    is PsiClass -> target.qualifiedName
                    is PsiMember -> target.containingClass?.qualifiedName
                    else -> null
                }
                check(node, qualifiedName)
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                val type = node.type
                val qualifiedName = when (type) {
                    is PsiClassType -> type.resolve()?.qualifiedName
                    else -> type.canonicalText
                }
                check(node, qualifiedName)
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve()
                check(node, method?.containingClass?.qualifiedName)
            }

            override fun visitImportStatement(node: UImportStatement) {
                val ref = node.importReference
                check(node, ref?.asSourceString())
            }

            private fun check(node: UElement, qualifiedName: String?) {
                if (qualifiedName.isNullOrEmpty()) return

                if (OLD_FE10_CLASSES.any {
                        qualifiedName == it || qualifiedName.startsWith("$it.")
                    }
                ) {
                    report(node, qualifiedName)
                } else if (OLD_FE10_PACKAGES.any { qualifiedName.startsWith(it) }) {
                    report(node, qualifiedName)
                }
            }

            private fun report(node: UElement, qualifiedName: String) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using old K1 Kotlin compiler API: $qualifiedName"
                )
            }
        }
}