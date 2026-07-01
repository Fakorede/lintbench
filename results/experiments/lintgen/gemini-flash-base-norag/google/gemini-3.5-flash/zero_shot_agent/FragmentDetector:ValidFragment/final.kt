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
            "android.support.v4.app.Fragment",
            "androidx.fragment.app.Fragment"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (evaluator.isAbstract(declaration)) {
            return
        }

        val isAndroidX = evaluator.inheritsFrom(declaration, "androidx.fragment.app.Fragment", false)

        // 1. Must be public
        if (!evaluator.isPublic(declaration)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public (${declaration.qualifiedName})"
            )
            return
        }

        // 2. Must not be a non-static inner class
        if (declaration.containingClass != null && !evaluator.isStatic(declaration)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment must be a static class (${declaration.qualifiedName})"
            )
            return
        }

        // 3. Must have a public no-arg constructor (only for non-AndroidX fragments)
        if (!isAndroidX) {
            val constructors = declaration.constructors
            if (constructors.isNotEmpty()) {
                var hasPublicNoArgConstructor = false
                var hasNoArgConstructor = false

                for (constructor in constructors) {
                    if (constructor.parameterList.parametersCount == 0) {
                        hasNoArgConstructor = true
                        if (evaluator.isPublic(constructor)) {
                            hasPublicNoArgConstructor = true
                        }
                    }
                }

                if (!hasPublicNoArgConstructor) {
                    val message = if (hasNoArgConstructor) {
                        "The default constructor of this fragment must be public"
                    } else {
                        "Avoid non-default constructors in fragments: use default constructor with Arguments instead"
                    }
                    context.report(
                        ISSUE,
                        declaration,
                        context.getNameLocation(declaration),
                        message
                    )
                }
            }
        }
    }

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
    }
}