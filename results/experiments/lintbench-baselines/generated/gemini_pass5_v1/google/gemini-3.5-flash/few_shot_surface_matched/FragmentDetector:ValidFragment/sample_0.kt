package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
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
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                FragmentDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(
            "android.app.Fragment",
            "android.support.v4.app.Fragment",
            "androidx.fragment.app.Fragment"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (evaluator.isAbstract(declaration)) {
            return
        }

        val name = declaration.name ?: return

        // Check if it's an inner class (must be static)
        val containingClass = declaration.containingClass
        if (containingClass != null && !evaluator.isStatic(declaration)) {
            context.report(
                Incident(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This fragment class should be public, static, and have a public empty constructor to be properly recreated by the system"
                )
            )
            return
        }

        // Must be public
        if (!evaluator.isPublic(declaration)) {
            context.report(
                Incident(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This fragment class should be public, static, and have a public empty constructor to be properly recreated by the system"
                )
            )
            return
        }

        val constructors = declaration.constructors
        if (constructors.isNotEmpty()) {
            var hasEmptyConstructor = false
            for (constructor in constructors) {
                if (constructor.parameterList.parametersCount == 0) {
                    if (evaluator.isPublic(constructor)) {
                        hasEmptyConstructor = true
                        break
                    }
                }
            }

            if (!hasEmptyConstructor) {
                context.report(
                    Incident(
                        ISSUE,
                        declaration,
                        context.getNameLocation(declaration),
                        "This fragment must have a public empty constructor"
                    )
                )
            }
        }
    }
}