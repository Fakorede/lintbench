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
            androidSpecific = true,
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()"
        )
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        FRAGMENT_CLASS,
        V4_FRAGMENT_CLASS,
        ANDROIDX_FRAGMENT_CLASS
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator

        // Skip androidx fragments - they support FragmentFactory
        if (evaluator.extendsClass(declaration.javaPsi, ANDROIDX_FRAGMENT_CLASS, false)) {
            return
        }

        // Skip abstract classes - they don't need to be instantiated directly
        if (declaration.isInterface || evaluator.isAbstract(declaration.javaPsi)) {
            return
        }

        // Inner classes must be static to be instantiatable
        if (declaration.javaPsi.containingClass != null) {
            if (!declaration.javaPsi.hasModifierProperty(PsiModifier.STATIC)) {
                context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This fragment inner class should be static (${declaration.qualifiedName})"
                )
                return
            }
        }

        // Check that the class is public
        if (!declaration.javaPsi.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public (${declaration.qualifiedName})"
            )
            return
        }

        // Check constructors
        val constructors = declaration.javaPsi.constructors
        if (constructors.isEmpty()) {
            // No explicit constructors means there's an implicit default constructor - that's fine
            return
        }

        // Look for a public no-arg constructor
        var hasDefaultConstructor = false
        for (constructor in constructors) {
            if (constructor.parameterList.parametersCount == 0) {
                hasDefaultConstructor = true
                if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                    context.report(
                        ISSUE,
                        declaration,
                        context.getLocation(constructor as com.intellij.psi.PsiElement),
                        "The default constructor must be public in ${declaration.qualifiedName}"
                    )
                    return
                }
                break
            }
        }

        if (!hasDefaultConstructor) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment should provide a default constructor (a public constructor with no arguments) (${declaration.qualifiedName})"
            )
        }
    }
}