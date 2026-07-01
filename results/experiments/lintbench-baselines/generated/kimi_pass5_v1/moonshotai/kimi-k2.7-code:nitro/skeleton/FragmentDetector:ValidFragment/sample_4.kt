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
        private const val ANDROID_APP_FRAGMENT = "android.app.Fragment"
        private const val ANDROID_SUPPORT_FRAGMENT = "android.support.v4.app.Fragment"
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
                From the Fragment documentation:

                **Every** fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with `setArguments(Bundle)` and later retrieved by the Fragment with `getArguments()`.

                Note that this is no longer true when you are using `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply any arguments you want (as of version androidx version 1.1).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        ANDROID_APP_FRAGMENT,
        ANDROID_SUPPORT_FRAGMENT,
        ANDROIDX_FRAGMENT,
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val psiClass = declaration.javaPsi ?: return

        // androidx.fragment.app.Fragment supports FragmentFactory, so the no-arg
        // constructor requirement does not apply there.
        if (context.evaluator.extendsClass(psiClass, ANDROIDX_FRAGMENT, false)) {
            return
        }

        if (psiClass.isInterface || psiClass.isEnum || psiClass.isAnnotationType) {
            return
        }
        if (psiClass.modifierList?.hasModifierProperty(PsiModifier.ABSTRACT) == true) {
            return
        }

        val name = declaration.name ?: "This Fragment"

        if (psiClass.containingClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "$name must be a static nested class"
            )
            return
        }

        if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "$name must be public"
            )
            return
        }

        if (!hasPublicNoArgConstructor(psiClass)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "$name must have a public no-arg constructor"
            )
        }
    }

    private fun hasPublicNoArgConstructor(psiClass: PsiClass): Boolean {
        val constructors = psiClass.constructors
        if (constructors.isEmpty()) {
            // No explicit constructors means the compiler generates a default
            // no-arg constructor with the same visibility as the class.
            return psiClass.hasModifierProperty(PsiModifier.PUBLIC)
        }
        return constructors.any { isPublicNoArgConstructor(it) }
    }

    private fun isPublicNoArgConstructor(constructor: PsiMethod): Boolean {
        return constructor.parameterList.parametersCount == 0
                && constructor.hasModifierProperty(PsiModifier.PUBLIC)
    }
}