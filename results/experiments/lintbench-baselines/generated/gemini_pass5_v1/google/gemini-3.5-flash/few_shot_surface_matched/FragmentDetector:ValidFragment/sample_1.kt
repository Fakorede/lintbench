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
import org.jetbrains.uast.UClass

class FragmentDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that subclasses \
                do not have other constructors with parameters, since these constructors will \
                not be called when the fragment is re-instantiated; instead, arguments can be \
                supplied by the caller with `setArguments(Bundle)` and later retrieved by the \
                Fragment with `getArguments()`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf("android.app.Fragment")
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (declaration.isInterface || evaluator.isAbstract(declaration)) {
            return
        }

        // 1. Must be public
        if (!evaluator.isPublic(declaration)) {
            val message = "This fragment class should be public (${declaration.qualifiedName})"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            return
        }

        // 2. Must not be a non-static inner class
        if (declaration.containingClass != null && !evaluator.isStatic(declaration)) {
            val message = "This fragment class should be static (${declaration.qualifiedName})"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            return
        }

        // 3. Check constructors
        val constructors = declaration.constructors
        if (constructors.isNotEmpty()) {
            var hasEmptyConstructor = false
            var isEmptyConstructorPublic = false

            for (constructor in constructors) {
                if (constructor.parameterList.parameters.isEmpty()) {
                    hasEmptyConstructor = true
                    if (evaluator.isPublic(constructor)) {
                        isEmptyConstructorPublic = true
                    }
                }
            }

            if (!hasEmptyConstructor) {
                val message = "This fragment must have a constructor with no arguments (${declaration.qualifiedName})"
                context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            } else if (!isEmptyConstructorPublic) {
                val message = "The default constructor of this fragment must be public (${declaration.qualifiedName})"
                context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            }
        }
    }
}