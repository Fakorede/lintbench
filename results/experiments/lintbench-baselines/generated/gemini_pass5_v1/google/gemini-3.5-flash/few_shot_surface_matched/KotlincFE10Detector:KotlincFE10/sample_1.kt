package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UClassLiteralExpression::class.java,
            UCallableReferenceExpression::class.java,
            UParameter::class.java,
            UTypeReferenceExpression::class.java,
            USimpleNameReferenceExpression::class.java,
            UCallExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
                val type = node.type
                if (type != null && isFe10Api(type.canonicalText)) {
                    report(node, type.canonicalText)
                }
            }

            override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
                val type = node.qualifierType
                if (type != null && isFe10Api(type.canonicalText)) {
                    report(node, type.canonicalText)
                }
            }

            override fun visitParameter(node: UParameter) {
                val type = node.type
                if (isFe10Api(type.canonicalText)) {
                    report(node, type.canonicalText)
                }
            }

            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                val type = node.type
                if (isFe10Api(type.canonicalText)) {
                    report(node, type.canonicalText)
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiClass) {
                    val fqName = resolved.qualifiedName
                    if (isFe10Api(fqName)) {
                        report(node, fqName ?: node.identifier)
                    }
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve()
                if (method != null) {
                    val containingClass = method.containingClass
                    val fqName = containingClass?.qualifiedName
                    if (isFe10Api(fqName)) {
                        report(node, "$fqName.${method.name}")
                    }
                }
            }

            private fun isFe10Api(fqName: String?): Boolean {
                if (fqName == null) return false
                return fqName.startsWith("org.jetbrains.kotlin.descriptors") ||
                        fqName.startsWith("org.jetbrains.kotlin.resolve") ||
                        fqName.startsWith("org.jetbrains.kotlin.types")
            }

            private fun report(node: UElement, apiName: String) {
                val incident = Incident(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid using old K1 Kotlin compiler APIs ($apiName)"
                )
                context.report(incident)
            }
        }
    }
}