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
import com.intellij.psi.PsiMember
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend (FE1.0) if possible, such as \
                `BindingContext`, `DeclarationDescriptor`, `KotlinType`, and other classes from \
                `org.jetbrains.kotlin.resolve`, `org.jetbrains.kotlin.descriptors`, or `org.jetbrains.kotlin.types`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val FE10_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types"
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UImportStatement::class.java, UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importReference = node.importReference ?: return
                val resolvedName = importReference.asSourceString()
                if (isFe10Api(resolvedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs ($resolvedName)"
                    )
                }
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                if (isInsideImport(node)) {
                    return
                }

                val resolvedName = node.asSourceString()
                if (isFe10Api(resolvedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs ($resolvedName)"
                    )
                } else {
                    val resolved = node.resolve() ?: return
                    val qualifiedName = when (resolved) {
                        is PsiClass -> resolved.qualifiedName
                        is PsiMember -> resolved.containingClass?.qualifiedName
                        else -> null
                    }
                    if (qualifiedName != null && isFe10Api(qualifiedName)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid using old K1 Kotlin compiler APIs ($qualifiedName)"
                        )
                    }
                }
            }
        }
    }

    private fun isInsideImport(node: UElement): Boolean {
        var current: UElement? = node
        while (current != null) {
            if (current is UImportStatement) {
                return true
            }
            current = current.uastParent
        }
        return false
    }

    private fun isFe10Api(qualifiedName: String): Boolean {
        return FE10_PREFIXES.any { prefix ->
            qualifiedName == prefix || qualifiedName.startsWith("$prefix.")
        }
    }
}