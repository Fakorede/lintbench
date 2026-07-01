package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

class KotlincFE10Detector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UImportStatement::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importReference = node.importReference ?: return
                val fqName = importReference.asSourceString()

                if (isFe10Api(fqName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler APIs"
                    )
                }
            }
        }
    }

    private fun isFe10Api(fqName: String): Boolean {
        val cleanName = fqName.removeSuffix(".*").trim()
        return cleanName == "org.jetbrains.kotlin.resolve" || cleanName.startsWith("org.jetbrains.kotlin.resolve.") ||
               cleanName == "org.jetbrains.kotlin.descriptors" || cleanName.startsWith("org.jetbrains.kotlin.descriptors.") ||
               cleanName == "org.jetbrains.kotlin.analyzer" || cleanName.startsWith("org.jetbrains.kotlin.analyzer.") ||
               cleanName == "org.jetbrains.kotlin.container" || cleanName.startsWith("org.jetbrains.kotlin.container.") ||
               cleanName.startsWith("org.jetbrains.kotlin.types.KotlinType") ||
               cleanName.startsWith("org.jetbrains.kotlin.types.TypeConstructor") ||
               cleanName.startsWith("org.jetbrains.kotlin.types.SimpleType") ||
               cleanName.startsWith("org.jetbrains.kotlin.types.FlexibleType")
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Avoid using old K1 Kotlin compiler APIs",
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
}