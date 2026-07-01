package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass

class FragmentDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()"
        )

        private const val FRAGMENT_CLASS = "android.app.Fragment"
        private const val SUPPORT_FRAGMENT_CLASS = "androidx.fragment.app.Fragment"
        private const val LEGACY_SUPPORT_FRAGMENT_CLASS = "android.support.v4.app.Fragment"
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(FRAGMENT_CLASS, LEGACY_SUPPORT_FRAGMENT_CLASS)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // Skip interfaces and abstract classes
        if (declaration.isInterface) return
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) return

        // Only check android.app.Fragment subclasses (not androidx)
        // androidx.fragment.app.Fragment supports FragmentFactory so no restriction
        val evaluator = context.evaluator

        val isAndroidFragment = evaluator.extendsClass(declaration, FRAGMENT_CLASS, false)
        val isLegacySupportFragment = evaluator.extendsClass(declaration, LEGACY_SUPPORT_FRAGMENT_CLASS, false)
        val isAndroidXFragment = evaluator.extendsClass(declaration, SUPPORT_FRAGMENT_CLASS, false)

        // If it's an androidx fragment, skip (FragmentFactory handles instantiation)
        if (isAndroidXFragment && !isAndroidFragment && !isLegacySupportFragment) return

        // The class must be a concrete fragment subclass
        if (!isAndroidFragment && !isLegacySupportFragment) return

        // Inner classes must be static
        if (declaration.containingClass != null) {
            if (!declaration.hasModifierProperty(PsiModifier.STATIC)) {
                context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This fragment inner class should be static (${declaration.qualifiedName})"
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
                "This fragment class should be public (${declaration.qualifiedName})"
            )
            return
        }

        // Check for a public no-argument constructor
        val constructors = declaration.constructors
        if (constructors.isEmpty()) {
            // No explicit constructors means there's an implicit default constructor — that's fine
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
                        constructor,
                        context.getNameLocation(constructor),
                        "The default constructor must be public in ${declaration.qualifiedName}"
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
                "This fragment should provide a default constructor (a public constructor " +
                    "with no arguments) (${declaration.qualifiedName})"
            )
        }
    }
}