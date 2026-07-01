package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.*

class FragmentDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            FragmentDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that subclasses \
                do not have other constructors with parameters, since these constructors will \
                not be called when the fragment is re-instantiated; instead, arguments can be \
                supplied by the caller with `setArguments(Bundle)` and later retrieved by the \
                Fragment with `getArguments()`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "android.app.Fragment",
            "android.support.v4.app.Fragment",
            "androidx.fragment.app.Fragment"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (declaration.name == null || declaration.isInterface || evaluator.isAbstract(declaration)) {
            return
        }

        // 1. Check if the class is public
        if (!evaluator.isPublic(declaration)) {
            val message = "This fragment class should be public (${declaration.qualifiedName})"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            return
        }

        // 2. Check if nested class is static
        if (declaration.containingClass != null && !evaluator.isStatic(declaration)) {
            val message = "This fragment must be a static class (${declaration.qualifiedName})"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            return
        }

        // 3. Check constructors
        val constructors = declaration.constructors
        if (constructors.isNotEmpty()) {
            var hasPublicNoArg = false
            for (constructor in constructors) {
                if (constructor.parameterList.parametersCount == 0) {
                    if (evaluator.isPublic(constructor)) {
                        hasPublicNoArg = true
                        break
                    }
                }
            }

            if (!hasPublicNoArg) {
                val message = "This fragment should provide a default constructor (a public constructor with no arguments) (${declaration.qualifiedName})"
                context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            }
        }
    }
}