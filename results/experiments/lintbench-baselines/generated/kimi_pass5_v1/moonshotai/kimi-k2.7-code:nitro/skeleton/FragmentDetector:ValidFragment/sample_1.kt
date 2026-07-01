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
        private const val ANDROID_APP_FRAGMENT = "android.app.Fragment"
        private const val ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment"

        private val IMPLEMENTATION = Implementation(
            FragmentDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have a public, no-arg constructor so the framework can
                re-instantiate it when restoring the activity's state.

                If you add a constructor that takes arguments, the framework will not call it
                when re-creating the fragment. Instead, supply arguments with setArguments(Bundle)
                and retrieve them with getArguments().

                Fragments must also be public, top-level or static nested classes. Non-static
                inner fragments, private fragments, or fragments without a public no-arg
                constructor cannot be instantiated by the framework.

                Note: This requirement does not apply to androidx.fragment.app.Fragment
                version 1.1.0 and higher, which can use a FragmentFactory to supply arguments.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        ANDROID_APP_FRAGMENT,
        ANDROIDX_FRAGMENT,
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isInterface) return
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) return

        // androidx.fragment.app.Fragment 1.1+ supports FragmentFactory, so the default
        // constructor requirement no longer applies.
        if (context.evaluator.extendsClass(declaration, ANDROIDX_FRAGMENT, false)) return

        if (!declaration.hasModifierProperty(PsiModifier.PUBLIC)) {
            report(
                context,
                declaration,
                "Fragment class must be public so the framework can re-instantiate it."
            )
            return
        }

        val outer = declaration.containingClass
        if (outer != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            report(
                context,
                declaration,
                "Fragments should not be non-static inner classes; they cannot be re-instantiated by the framework."
            )
            return
        }

        val constructors = declaration.constructors
        // No explicit constructors means Java provides a public no-arg constructor.
        if (constructors.isEmpty()) return

        val hasPublicNoArgConstructor = constructors.any {
            it.parameterList.parametersCount == 0 &&
                it.hasModifierProperty(PsiModifier.PUBLIC)
        }

        if (!hasPublicNoArgConstructor) {
            report(
                context,
                declaration,
                "This fragment should provide a public, no-arg constructor so it can be re-instantiated by the framework."
            )
        }
    }

    private fun report(context: JavaContext, declaration: UClass, message: String) {
        context.report(
            ISSUE,
            declaration,
            context.getNameLocation(declaration),
            message,
        )
    }
}