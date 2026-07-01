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
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass

class FragmentDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FRAGMENT_CLASS = "android.app.Fragment"
        private const val V4_FRAGMENT_CLASS = "android.support.v4.app.Fragment"
        private const val ANDROIDX_FRAGMENT_CLASS = "androidx.fragment.app.Fragment"

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                From the Fragment documentation:
                **Every** fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that subclasses \
                do not have other constructors with parameters, since these constructors will \
                not be called when the fragment is re-instantiated; instead, arguments can be \
                supplied by the caller with `setArguments(Bundle)` and later retrieved by the \
                Fragment with `getArguments()`.

                Note that this is no longer true when you are using \
                `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply \
                any arguments you want (as of version androidx version 1.1).
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                FragmentDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(FRAGMENT_CLASS, V4_FRAGMENT_CLASS, ANDROIDX_FRAGMENT_CLASS)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator

        // Skip abstract classes — they don't need to be instantiated directly
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return
        }

        // If this is an androidx fragment, the FragmentFactory handles instantiation,
        // so we don't need to enforce the empty constructor rule.
        if (evaluator.extendsClass(declaration, ANDROIDX_FRAGMENT_CLASS, false)) {
            return
        }

        // Inner (non-static) classes cannot be instantiated by the framework
        if (declaration.containingClass != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragments should be static inner classes or top-level classes"
            )
            return
        }

        // The class must be public so it can be instantiated by the framework
        if (!declaration.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragments should be public"
            )
            return
        }

        // Check for a public no-argument constructor
        val constructors = declaration.constructors
        if (constructors.isEmpty()) {
            // No explicit constructors means the default no-arg constructor is used — that's fine
            return
        }

        var hasDefaultConstructor = false
        for (constructor in constructors) {
            if (constructor.parameterList.parametersCount == 0) {
                if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                    hasDefaultConstructor = true
                } else {
                    // There is a no-arg constructor but it's not public
                    context.report(
                        ISSUE,
                        constructor as PsiMethod,
                        context.getLocation(constructor as PsiMethod),
                        "The default constructor must be public"
                    )
                    return
                }
            }
        }

        if (!hasDefaultConstructor) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment should provide a public no-argument constructor (a temporary instance " +
                    "of this fragment will be created to initialize the fragment)"
            )
        }
    }
}