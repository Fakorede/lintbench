package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass

class FragmentDetector : Detector(), Detector.SourceCodeScanner {

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
                Every fragment must have an empty constructor, so it can be instantiated
                when restoring its activity's state. It is strongly recommended that
                subclasses do not have other constructors with parameters, since these
                constructors will not be called when the fragment is re-instantiated;
                instead, arguments can be supplied by the caller with setArguments(Bundle)
                and later retrieved by the Fragment with getArguments().

                Note that this is no longer true when you are using
                androidx.fragment.app.Fragment; with the FragmentFactory you can supply
                any arguments you want (as of version androidx version 1.1).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "android.app.Fragment",
            "android.support.v4.app.Fragment",
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val psiClass = declaration.javaPsi ?: return
        if (psiClass.name == null) return
        if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum) return
        if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return

        if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            report(context, declaration, "Fragment subclasses must be public")
            return
        }

        if (psiClass.containingClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            report(context, declaration, "Fragment inner classes must be static")
            return
        }

        val constructors = psiClass.constructors
        if (constructors.isNotEmpty() && constructors.none { isPublicNoArgConstructor(it) }) {
            report(context, declaration, "Fragment must provide a public empty constructor")
        }
    }

    private fun isPublicNoArgConstructor(method: PsiMethod): Boolean {
        return method.isConstructor &&
                method.hasModifierProperty(PsiModifier.PUBLIC) &&
                method.parameterList.parametersCount == 0
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