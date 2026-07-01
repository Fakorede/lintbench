package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class KotlincFE10Detector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "KotlincFE10",
            briefDescription = "Using old K1 Kotlin compiler APIs",
            explanation = """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, \
                is coming.  Try to avoid using internal APIs from the old frontend if possible.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                KotlincFE10Detector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val FE10_PACKAGES = listOf(
            "org.jetbrains.kotlin.descriptors",
            "org.jetbrains.kotlin.resolve",
            "org.jetbrains.kotlin.types",
            "org.jetbrains.kotlin.builtins",
            "org.jetbrains.kotlin.psi.psiUtil",
            "org.jetbrains.kotlin.idea.caches.resolve",
            "org.jetbrains.kotlin.idea.resolve",
            "org.jetbrains.kotlin.idea.caches",
            "org.jetbrains.kotlin.idea.core",
            "org.jetbrains.kotlin.idea.util",
            "org.jetbrains.kotlin.idea.search",
            "org.jetbrains.kotlin.idea.refactoring",
            "org.jetbrains.kotlin.idea.inspections",
            "org.jetbrains.kotlin.idea.quickfix",
            "org.jetbrains.kotlin.idea.intentions",
            "org.jetbrains.kotlin.idea.highlighter",
            "org.jetbrains.kotlin.idea.completion",
            "org.jetbrains.kotlin.idea.findUsages",
            "org.jetbrains.kotlin.idea.imports",
            "org.jetbrains.kotlin.idea.kdoc",
            "org.jetbrains.kotlin.idea.navigation",
            "org.jetbrains.kotlin.idea.parameterInfo",
            "org.jetbrains.kotlin.idea.references",
            "org.jetbrains.kotlin.idea.stubindex",
            "org.jetbrains.kotlin.idea.vfilefinder",
            "org.jetbrains.kotlin.idea.versions",
            "org.jetbrains.kotlin.idea.configuration",
            "org.jetbrains.kotlin.idea.framework",
            "org.jetbrains.kotlin.idea.project",
            "org.jetbrains.kotlin.idea.debugger",
            "org.jetbrains.kotlin.idea.decompiler",
            "org.jetbrains.kotlin.idea.editor",
            "org.jetbrains.kotlin.idea.formatter",
            "org.jetbrains.kotlin.idea.folding",
            "org.jetbrains.kotlin.idea.hierarchy",
            "org.jetbrains.kotlin.idea.liveTemplates",
            "org.jetbrains.kotlin.idea.maven",
            "org.jetbrains.kotlin.idea.run",
            "org.jetbrains.kotlin.idea.scratch",
            "org.jetbrains.kotlin.idea.script",
            "org.jetbrains.kotlin.idea.slicer",
            "org.jetbrains.kotlin.idea.statistics",
            "org.jetbrains.kotlin.idea.testing",
            "org.jetbrains.kotlin.idea.tooling",
            "org.jetbrains.kotlin.idea.update",
            "org.jetbrains.kotlin.idea.vfilefinder",
            "org.jetbrains.kotlin.idea.xml",
            "org.jetbrains.kotlin.idea.yaml",
            "org.jetbrains.kotlin.idea.gradle",
            "org.jetbrains.kotlin.idea.compiler",
            "org.jetbrains.kotlin.idea.facet",
            "org.jetbrains.kotlin.idea.inspections",
            "org.jetbrains.kotlin.idea.j2k",
            "org.jetbrains.kotlin.idea.klib",
            "org.jetbrains.kotlin.idea.platform",
            "org.jetbrains.kotlin.idea.roots",
            "org.jetbrains.kotlin.idea.run",
            "org.jetbrains.kotlin.idea.search",
            "org.jetbrains.kotlin.idea.structureView",
            "org.jetbrains.kotlin.idea.testIntegration",
            "org.jetbrains.kotlin.idea.vfilefinder",
            "org.jetbrains.kotlin.idea.versions",
            "org.jetbrains.kotlin.idea.workspaceModel",
            "org.jetbrains.kotlin.load",
            "org.jetbrains.kotlin.serialization",
            "org.jetbrains.kotlin.metadata",
            "org.jetbrains.kotlin.incremental",
            "org.jetbrains.kotlin.backend.common",
            "org.jetbrains.kotlin.backend.jvm",
            "org.jetbrains.kotlin.codegen",
            "org.jetbrains.kotlin.js.translate",
            "org.jetbrains.kotlin.js.backend",
            "org.jetbrains.kotlin.js.facade",
            "org.jetbrains.kotlin.js.parser",
            "org.jetbrains.kotlin.js.sourceMap",
            "org.jetbrains.kotlin.js.inline",
            "org.jetbrains.kotlin.js.coroutine",
            "org.jetbrains.kotlin.js.dce",
            "org.jetbrains.kotlin.js.naming",
            "org.jetbrains.kotlin.js.test",
            "org.jetbrains.kotlin.js.config",
            "org.jetbrains.kotlin.js.analyze",
            "org.jetbrains.kotlin.js.resolve",
            "org.jetbrains.kotlin.js.descriptors",
            "org.jetbrains.kotlin.js.patterns",
            "org.jetbrains.kotlin.js.translate.context",
            "org.jetbrains.kotlin.js.translate.declaration",
            "org.jetbrains.kotlin.js.translate.expression",
            "org.jetbrains.kotlin.js.translate.general",
            "org.jetbrains.kotlin.js.translate.initializer",
            "org.jetbrains.kotlin.js.translate.intrinsic",
            "org.jetbrains.kotlin.js.translate.operation",
            "org.jetbrains.kotlin.js.translate.reference",
            "org.jetbrains.kotlin.js.translate.test",
            "org.jetbrains.kotlin.js.translate.utils",
            "org.jetbrains.kotlin.fir.analysis",
            "org.jetbrains.kotlin.fir.backend",
            "org.jetbrains.kotlin.fir.builder",
            "org.jetbrains.kotlin.fir.declarations",
            "org.jetbrains.kotlin.fir.deserialization",
            "org.jetbrains.kotlin.fir.expressions",
            "org.jetbrains.kotlin.fir.java",
            "org.jetbrains.kotlin.fir.lightTree",
            "org.jetbrains.kotlin.fir.references",
            "org.jetbrains.kotlin.fir.resolve",
            "org.jetbrains.kotlin.fir.scopes",
            "org.jetbrains.kotlin.fir.serialization",
            "org.jetbrains.kotlin.fir.session",
            "org.jetbrains.kotlin.fir.symbols",
            "org.jetbrains.kotlin.fir.types",
            "org.jetbrains.kotlin.fir.visitors",
            "org.jetbrains.kotlin.analysis.api",
            "org.jetbrains.kotlin.analysis.low.level.api.fir",
            "org.jetbrains.kotlin.analysis.providers",
            "org.jetbrains.kotlin.analysis.project.structure",
        )

        private val FE10_CLASS_PREFIXES = listOf(
            "org.jetbrains.kotlin.descriptors.",
            "org.jetbrains.kotlin.resolve.",
            "org.jetbrains.kotlin.types.",
            "org.jetbrains.kotlin.builtins.",
            "org.jetbrains.kotlin.load.",
            "org.jetbrains.kotlin.serialization.",
            "org.jetbrains.kotlin.metadata.",
            "org.jetbrains.kotlin.incremental.",
            "org.jetbrains.kotlin.codegen.",
            "org.jetbrains.kotlin.backend.jvm.",
            "org.jetbrains.kotlin.backend.common.",
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UImportStatement::class.java,
            UCallExpression::class.java,
            UReferenceExpression::class.java
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitImportStatement(node: UImportStatement) {
                val importRef = node.importReference?.asSourceString() ?: return
                if (isFE10Import(importRef)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Avoid using old K1 Kotlin compiler API `$importRef`; " +
                                "prefer K2/Analysis API equivalents"
                    )
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method: PsiMethod = node.resolve() ?: return
                val containingClass = method.containingClass ?: return
                val qualifiedName = containingClass.qualifiedName ?: return
                if (isFE10ClassName(qualifiedName)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getCallLocation(node, includeReceiver = false, includeArguments = false),
                        "Avoid using old K1 Kotlin compiler API `$qualifiedName`; " +
                                "prefer K2/Analysis API equivalents"
                    )
                }
            }

            override fun visitReferenceExpression(node: UReferenceExpression) {
                // handled via imports and call expressions
            }
        }
    }

    private fun isFE10Import(importRef: String): Boolean {
        return FE10_CLASS_PREFIXES.any { importRef.startsWith(it) }
    }

    private fun isFE10ClassName(qualifiedName: String): Boolean {
        return FE10_CLASS_PREFIXES.any { qualifiedName.startsWith(it) }
    }
}