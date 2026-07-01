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

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "android.app.Fragment",
            "android.support.v4.app.Fragment"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (evaluator.isAbstract(declaration)) {
            return
        }

        // Avoid checking anonymous or local classes
        if (declaration.name == null || evaluator.isAnonymous(declaration)) {
            return
        }

        // 1. Must be public
        if (!evaluator.isPublic(declaration)) {
            val message = "This fragment class should be public"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            return
        }

        // 2. Must not be a non-static inner class
        if (declaration.containingClass != null && !evaluator.isStatic(declaration)) {
            val message = "This fragment class should be public which requires it to be static (not an inner class)"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            return
        }

        // 3. Must have a public empty constructor
        val constructors = declaration.constructors
        if (constructors.isNotEmpty()) {
            var hasBlankConstructor = false
            var hasConstructor = false
            for (constructor in constructors) {
                hasConstructor = true
                if (constructor.parameterList.parametersCount == 0) {
                    if (evaluator.isPublic(constructor)) {
                        hasBlankConstructor = true
                        break
                    }
                }
            }

            if (hasConstructor && !hasBlankConstructor) {
                val message = "This fragment must have a public empty constructor for dependency injection / reconstruction"
                context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
    }
}