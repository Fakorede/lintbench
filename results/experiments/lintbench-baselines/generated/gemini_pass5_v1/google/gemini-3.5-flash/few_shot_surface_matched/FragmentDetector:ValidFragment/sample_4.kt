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
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty constructor, so it can be instantiated when \
                restoring its activity's state. It is strongly recommended that subclasses do not \
                have other constructors with parameters, since these constructors will not be \
                called when the fragment is re-instantiated; instead, arguments can be supplied \
                by the caller with `setArguments(Bundle)` and later retrieved by the Fragment \
                with `getArguments()`.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                FragmentDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf("android.app.Fragment")
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (evaluator.isAbstract(declaration) || declaration.isInterface) {
            return
        }

        val isPublic = declaration.hasModifierProperty(PsiModifier.PUBLIC) ||
                (!declaration.hasModifierProperty(PsiModifier.PRIVATE) &&
                        !declaration.hasModifierProperty(PsiModifier.PROTECTED))

        if (!isPublic) {
            val message = "This fragment class should be public"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            return
        }

        val containingClass = declaration.containingClass
        if (containingClass != null && !evaluator.isStatic(declaration)) {
            val message = "This fragment class should be public static"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            return
        }

        val constructors = declaration.constructors
        if (constructors.isNotEmpty()) {
            var hasEmptyConstructor = false
            var hasPublicEmptyConstructor = false
            for (constructor in constructors) {
                if (constructor.parameterList.parametersCount == 0) {
                    hasEmptyConstructor = true
                    if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                        hasPublicEmptyConstructor = true
                    }
                }
            }

            if (!hasEmptyConstructor) {
                val message = "This fragment must have an empty constructor (a public constructor with no arguments)"
                context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            } else if (!hasPublicEmptyConstructor) {
                val message = "The default constructor of this fragment must be public"
                context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            }
        }
    }
}