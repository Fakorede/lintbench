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
import org.jetbrains.uast.UReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitReference(node: UReferenceExpression) {
                val resolved = node.resolve()
                val qualifiedName = when (resolved) {
                    is PsiClass -> resolved.qualifiedName
                    is PsiMember -> resolved.containingClass?.qualifiedName
                    else -> null
                } ?: return

                for (prefix in K1_PREFIXES) {
                    if (qualifiedName.startsWith(prefix)) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Avoid using old K1 Kotlin compiler APIs (`$qualifiedName`)"
                        )
                        return
                    }
                }
            }
        }
    }

    companion object {
        private val K1_PREFIXES = listOf(
            "org.jetbrains.kotlin.psi.",
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.cfg.",
            "org.jetbrains.kotlin.js.",
            "org.jetbrains.kotlin.serialization.",
            "org.jetbrains.kotlin.cli.",
            "org.jetbrains.kotlin.config.",
            "org.jetbrains.kotlin.container.",
            "org.jetbrains.kotlin.load.java.",
            "org.jetbrains.kotlin.load.kotlin.",
            "org.jetbrains.kotlin.metadata.",
            "org.jetbrains.kotlin.name.",
            "org.jetbrains.kotlin.platform.",
            "org.jetbrains.kotlin.storage.",
            "org.jetbrains.kotlin.utils.",
            "org.jetbrains.kotlin.util.",
            "org.jetbrains.kotlin.frontend.",
            "org.jetbrains.kotlin.analyzer."
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
}