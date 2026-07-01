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
import org.jetbrains.uast.UMethod

class FragmentDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val FRAGMENT = "android.app.Fragment"
        private const val FRAGMENT_V4 = "android.support.v4.app.Fragment"

        private val IMPLEMENTATION = Implementation(
            FragmentDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty (public) constructor so the system can
                instantiate it when recreating the activity. Fragments should not define
                constructors that take arguments; use `setArguments(Bundle)` instead.

                Note: this restriction does not apply to `androidx.fragment.app.Fragment`
                when used with `FragmentFactory`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String> =
        listOf(FRAGMENT, FRAGMENT_V4)

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val psiClass = declaration.javaPsi

        if (psiClass.isInterface || psiClass.isEnum || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return
        }

        val containingClass = psiClass.containingClass
        if (containingClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragment inner classes must be static"
            )
            return
        }

        val constructors = declaration.methods.filter { it.isConstructor }

        val hasPublicDefaultConstructor = if (constructors.isEmpty()) {
            psiClass.hasModifierProperty(PsiModifier.PUBLIC)
        } else {
            constructors.any { method ->
                val parameterCount = method.javaPsi?.parameterList?.parametersCount ?: 0
                val isPublic = method.javaPsi?.hasModifierProperty(PsiModifier.PUBLIC) ?: false
                parameterCount == 0 && isPublic
            }
        }

        if (!hasPublicDefaultConstructor) {
            val name = declaration.name ?: psiClass.name ?: "This fragment"
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "$name must provide a public default constructor"
            )
        }

        for (method in constructors) {
            val parameterCount = method.javaPsi?.parameterList?.parametersCount ?: 0
            if (parameterCount > 0) {
                context.report(
                    ISSUE,
                    method,
                    context.getLocation(method),
                    "Avoid passing arguments to fragment constructors; " +
                        "use a default constructor plus Fragment#setArguments(Bundle) instead"
                )
            }
        }
    }
}