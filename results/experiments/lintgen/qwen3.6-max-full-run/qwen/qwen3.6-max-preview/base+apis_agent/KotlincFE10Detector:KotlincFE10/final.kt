package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.w3c.dom.Node

class KotlincFE10Detector : Detector(), SourceCodeScanner {
    companion object {
        private val K1_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.analyzer",
            "org.jetbrains.kotlin.load.java",
            "org.jetbrains.kotlin.load.kotlin",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.metadata"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = "K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. Try to avoid using internal APIs from the old frontend if possible.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(USimpleNameReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
                val resolved = node.resolve() ?: return
                val fqn = getQualifiedName(resolved) ?: return

                if (K1_PREFIXES.any { fqn.startsWith(it) }) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Using old K1 Kotlin compiler API (`$fqn`)"
                    )
                }
            }
        }
    }

    private fun getQualifiedName(element: PsiElement): String? {
        return when (element) {
            is PsiClass -> element.qualifiedName
            is PsiMember -> element.containingClass?.qualifiedName
            else -> null
        }
    }
}