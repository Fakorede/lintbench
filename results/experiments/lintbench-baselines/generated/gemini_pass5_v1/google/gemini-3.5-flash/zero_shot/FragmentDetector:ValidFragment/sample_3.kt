package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass

class FragmentDetector : Detector(), SourceCodeScanner {

    companion object {
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
            implementation = Implementation(
                FragmentDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private const val FRAGMENT_CLASS = "android.app.Fragment"
        private const val SUPPORT_FRAGMENT_CLASS = "android.support.v4.app.Fragment"
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf(FRAGMENT_CLASS, SUPPORT_FRAGMENT_CLASS)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (declaration.isInterface) {
            return
        }
        if (evaluator.isAbstract(declaration)) {
            return
        }
        val name = declaration.name ?: return

        // 1. Must be public
        if (!evaluator.isPublic(declaration)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public ($name)"
            )
            return
        }

        // 2. Must not be a non-static inner class
        if (declaration.containingClass != null && !evaluator.isStatic(declaration)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class must be a static class (or a top-level class) ($name)"
            )
            return
        }

        // 3. Must have a public empty constructor
        val constructors = declaration.constructors
        if (constructors.isNotEmpty()) {
            var hasEmptyConstructor = false
            for (constructor in constructors) {
                if (constructor.parameterList.parametersCount == 0) {
                    if (evaluator.isPublic(constructor)) {
                        hasEmptyConstructor = true
                        break
                    }
                }
            }
            if (!hasEmptyConstructor) {
                context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This fragment must have a public empty constructor ($name)"
                )
            }
        }
    }
}