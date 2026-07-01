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
        private val IMPLEMENTATION = Implementation(
            FragmentDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            implementation = IMPLEMENTATION,
        )

        private const val FRAGMENT_CLASS = "android.app.Fragment"
        private const val V4_FRAGMENT_CLASS = "android.support.v4.app.Fragment"
        // Note: androidx fragments with FragmentFactory are exempt, but we still check
        // the legacy ones. We include androidx here to catch cases where factory isn't used.
        private const val ANDROIDX_FRAGMENT_CLASS = "androidx.fragment.app.Fragment"
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        FRAGMENT_CLASS,
        V4_FRAGMENT_CLASS,
        ANDROIDX_FRAGMENT_CLASS,
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // androidx.fragment.app.Fragment with FragmentFactory support — skip
        if (context.evaluator.extendsClass(
                declaration.javaPsi,
                ANDROIDX_FRAGMENT_CLASS,
                false
            )
        ) {
            return
        }

        val psiClass = declaration.javaPsi

        // Abstract classes don't need to be instantiated directly
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return
        }

        // Inner (non-static) classes cannot be instantiated by the framework
        if (psiClass.containingClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment inner class should be static (${psiClass.qualifiedName})",
            )
            return
        }

        // The class must be public
        if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public (${psiClass.qualifiedName})",
            )
            return
        }

        // Check constructors: if there are explicit constructors, at least one must be
        // a public no-argument constructor.
        val constructors = psiClass.constructors
        if (constructors.isEmpty()) {
            // No explicit constructors means the default no-arg constructor is used — OK
            return
        }

        var hasDefaultConstructor = false
        for (constructor in constructors) {
            if (isDefaultConstructor(constructor)) {
                hasDefaultConstructor = true
                break
            }
        }

        if (!hasDefaultConstructor) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment should provide a default constructor (a public constructor " +
                    "with no arguments) (${psiClass.qualifiedName})",
            )
        }
    }

    private fun isDefaultConstructor(constructor: PsiMethod): Boolean {
        if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false
        }
        return constructor.parameterList.parametersCount == 0
    }
}