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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UImportStatement::class.java, UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            private val reportedLocations = mutableSetOf<String>()

            override fun visitImportStatement(node: UImportStatement) {
                val importExpression = node.importExpression ?: return
                val importText = importExpression.asSourceString()
                for (banned in BANNED_PREFIXES) {
                    if (importText.startsWith(banned)) {
                        report(node, banned)
                        break
                    }
                }
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve() ?: return
                val fqName = when (resolved) {
                    is PsiClass -> resolved.qualifiedName
                    is PsiMember -> resolved.containingClass?.qualifiedName
                    else -> null
                } ?: return

                for (banned in BANNED_PREFIXES) {
                    if (fqName.startsWith(banned)) {
                        report(node, banned)
                        break
                    }
                }
            }

            private fun report(node: UElement, banned: String) {
                val location = context.getLocation(node)
                val line = location.start?.line ?: -1
                val key = "$line:$banned"
                if (reportedLocations.add(key)) {
                    context.report(
                        ISSUE,
                        node,
                        location,
                        "Avoid using old K1 Kotlin compiler APIs ($banned)"
                    )
                }
            }
        }
    }

    companion object {
        private val BANNED_PREFIXES = listOf(
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.analyzer",
            "org.jetbrains.kotlin.container",
            "org.jetbrains.kotlin.types.KotlinType",
            "org.jetbrains.kotlin.types.TypeConstructor",
            "org.jetbrains.kotlin.types.TypeProjection",
            "org.jetbrains.kotlin.types.SimpleType",
            "org.jetbrains.kotlin.types.FlexibleType"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """.trimIndent(),
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