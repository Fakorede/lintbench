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
        // the legacy android.app.Fragment and support library fragments
        private const val ANDROIDX_FRAGMENT_CLASS = "androidx.fragment.app.Fragment"
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        FRAGMENT_CLASS,
        V4_FRAGMENT_CLASS,
        ANDROIDX_FRAGMENT_CLASS,
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // androidx.fragment.app.Fragment with FragmentFactory is exempt
        if (context.evaluator.extendsClass(
                declaration.javaPsi,
                ANDROIDX_FRAGMENT_CLASS,
                false
            )
        ) {
            // androidx fragments are exempt from this requirement since FragmentFactory
            // was introduced in androidx fragment 1.1
            return
        }

        // Abstract classes don't need to be instantiatable directly
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return
        }

        // Inner classes must be static
        val containingClass = declaration.javaPsi.containingClass
        if (containingClass != null) {
            if (!declaration.hasModifierProperty(PsiModifier.STATIC)) {
                context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This fragment inner class should be static (${declaration.qualifiedName})",
                )
                return
            }
        }

        // The class must be public
        if (!declaration.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public (${declaration.qualifiedName})",
            )
            return
        }

        // Check for a public no-argument constructor
        val constructors = declaration.javaPsi.constructors
        if (constructors.isEmpty()) {
            // No constructors defined means the default no-arg constructor is used — that's fine
            return
        }

        // Look for a public no-argument constructor
        var hasNoArgConstructor = false
        for (constructor in constructors) {
            if (constructor.parameterList.parametersCount == 0) {
                hasNoArgConstructor = true
                if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                    context.report(
                        ISSUE,
                        declaration,
                        context.getNameLocation(declaration),
                        "The default constructor must be public in ${declaration.qualifiedName}",
                    )
                    return
                }
                break
            }
        }

        if (!hasNoArgConstructor) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment should provide a default constructor (a public constructor with no arguments) (${declaration.qualifiedName})",
            )
        }
    }
}