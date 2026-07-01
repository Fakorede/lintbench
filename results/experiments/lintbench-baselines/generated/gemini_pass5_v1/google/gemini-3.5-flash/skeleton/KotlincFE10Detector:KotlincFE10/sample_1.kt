package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            KotlincFE10Detector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming.
                Try to avoid using internal APIs from the old frontend if possible.
            """.trimIndent(),
            category = Category.CUSTOM_LINT_CHECKS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
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

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            private val reportedOffsets = mutableSetOf<Int>()

            private fun isFe10(fqName: String?): Boolean {
                if (fqName == null) return false
                return fqName.contains("org.jetbrains.kotlin.resolve") ||
                       fqName.contains("org.jetbrains.kotlin.descriptors") ||
                       fqName.contains("org.jetbrains.kotlin.types") ||
                       fqName.contains("org.jetbrains.kotlin.diagnostics") ||
                       fqName.contains("org.jetbrains.kotlin.renderer") ||
                       fqName.contains("org.jetbrains.kotlin.analyzer") ||
                       fqName.contains("org.jetbrains.kotlin.container")
            }

            private fun report(node: UElement, apiName: String) {
                val location = context.getLocation(node)
                val startOffset = location.start?.offset ?: node.hashCode()
                if (reportedOffsets.add(startOffset)) {
                    context.report(
                        ISSUE,
                        node,
                        location,
                        "Avoid using old K1 Kotlin compiler APIs ($apiName)"
                    )
                }
            }

            override fun visitClassLiteralExpression(node: UElement) {
                if (node is UClassLiteralExpression) {
                    val type = node.type
                    if (type != null) {
                        val fqName = type.canonicalText
                        if (isFe10(fqName)) {
                            report(node, fqName)
                        }
                    }
                }
            }

            override fun visitCallableReferenceExpression(node: UElement) {
                if (node is UCallableReferenceExpression) {
                    val resolved = node.resolve()
                    if (resolved is PsiMethod) {
                        val containingClass = resolved.containingClass
                        val fqName = containingClass?.qualifiedName
                        if (isFe10(fqName)) {
                            report(node, "${fqName}.${resolved.name}")
                        }
                    } else if (resolved is PsiClass) {
                        val fqName = resolved.qualifiedName
                        if (isFe10(fqName)) {
                            report(node, fqName ?: "")
                        }
                    }
                }
            }

            override fun visitParameter(node: UElement) {
                if (node is UParameter) {
                    val fqName = node.type.canonicalText
                    if (isFe10(fqName)) {
                        report(node, fqName)
                    }
                }
            }

            override fun visitTypeReferenceExpression(node: UElement) {
                if (node is UTypeReferenceExpression) {
                    val fqName = node.type.canonicalText
                    if (isFe10(fqName)) {
                        report(node, fqName)
                    }
                }
            }

            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiClass) {
                    val fqName = resolved.qualifiedName
                    if (isFe10(fqName)) {
                        report(node, fqName ?: "")
                    }
                } else if (resolved is PsiMethod) {
                    val containingClass = resolved.containingClass
                    val fqName = containingClass?.qualifiedName
                    if (isFe10(fqName)) {
                        report(node, "${fqName}.${resolved.name}")
                    }
                } else if (resolved is PsiField) {
                    val containingClass = resolved.containingClass
                    val fqName = containingClass?.qualifiedName
                    if (isFe10(fqName)) {
                        report(node, "${fqName}.${resolved.name}")
                    }
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve()
                if (method != null) {
                    val containingClass = method.containingClass
                    val fqName = containingClass?.qualifiedName
                    if (isFe10(fqName)) {
                        report(node, "${fqName}.${method.name}")
                    }
                }
            }
        }
}