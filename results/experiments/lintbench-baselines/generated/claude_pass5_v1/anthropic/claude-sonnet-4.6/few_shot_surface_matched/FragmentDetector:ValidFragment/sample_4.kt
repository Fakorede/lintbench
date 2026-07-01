package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
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
            implementation = Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true,
        )
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(FRAGMENT_CLASS, V4_FRAGMENT_CLASS, ANDROIDX_FRAGMENT_CLASS)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val psiClass = declaration.javaPsi

        // Skip abstract classes — they don't need to be instantiated directly
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return
        }

        // Skip androidx fragments — FragmentFactory allows parameterized constructors
        if (isAndroidXFragment(context, psiClass)) {
            return
        }

        // The class must be public to be instantiatable
        if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public (`${psiClass.qualifiedName}`)"
            )
            return
        }

        // If the class is an inner class, it must be static
        if (psiClass.containingClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment inner class should be static (`${psiClass.qualifiedName}`)"
            )
            return
        }

        // Check constructors
        val constructors = psiClass.constructors
        if (constructors.isEmpty()) {
            // No explicit constructors means an implicit public no-arg constructor exists — OK
            return
        }

        // Look for a public no-argument constructor
        var hasDefaultConstructor = false
        for (constructor in constructors) {
            if (constructor.parameterList.parametersCount == 0) {
                hasDefaultConstructor = true
                if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                    // Has a no-arg constructor but it's not public
                    context.report(
                        ISSUE,
                        declaration,
                        context.getNameLocation(declaration),
                        "The default constructor of this fragment should be public (`${psiClass.qualifiedName}`)"
                    )
                    return
                }
                break
            }
        }

        if (!hasDefaultConstructor) {
            // There are constructors but none of them is a no-arg constructor
            val nonDefaultConstructor = constructors.firstOrNull { it.parameterList.parametersCount > 0 }
            val location = if (nonDefaultConstructor != null) {
                context.getNameLocation(nonDefaultConstructor as PsiMethod)
            } else {
                context.getNameLocation(declaration)
            }
            context.report(
                ISSUE,
                declaration,
                location,
                "This fragment should provide a default constructor (a public constructor with no arguments) (`${psiClass.qualifiedName}`)"
            )
        }
    }

    private fun isAndroidXFragment(context: JavaContext, psiClass: PsiClass): Boolean {
        val evaluator = context.evaluator
        return evaluator.extendsClass(psiClass, ANDROIDX_FRAGMENT_CLASS, false)
    }
}