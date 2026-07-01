package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

class FragmentDetector : Detector(), Detector.ClassScanner {

    override fun applicableSuperClasses(): List<String> = listOf(
        "android.app.Fragment",
        "android.support.v4.app.Fragment"
    )

    override fun visitClass(context: JavaContext, declaration: PsiClass) {
        if (declaration.isInterface || declaration.isAbstract) {
            return
        }

        val evaluator = context.evaluator

        if (!evaluator.isPublic(declaration)) {
            report(
                context,
                declaration,
                context.getLocation(declaration),
                "This fragment class should be public so it can be instantiated"
            )
            return
        }

        if (declaration.containingClass != null && !evaluator.isStatic(declaration)) {
            report(
                context,
                declaration,
                context.getLocation(declaration),
                "This fragment inner class should be static"
            )
            return
        }

        val constructors = declaration.constructors
        if (constructors.isEmpty()) {
            return
        }

        val hasPublicEmptyConstructor = constructors.any { constructor ->
            evaluator.isPublic(constructor) && constructor.parameterList.parametersCount == 0
        }

        if (!hasPublicEmptyConstructor) {
            report(
                context,
                declaration,
                context.getLocation(declaration),
                "This fragment should provide a public no-arg constructor"
            )
        }
    }

    private fun report(
        context: JavaContext,
        declaration: PsiClass,
        location: com.android.tools.lint.detector.api.Location,
        message: String
    ) {
        context.report(ISSUE, declaration, location, message)
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with `setArguments(Bundle)` and later retrieved by the Fragment with `getArguments()`.

                Note that this is no longer true when you are using `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply any arguments you want (as of version androidx version 1.1).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(FragmentDetector::class.java, Scope.CLASS_FILE_SCOPE)
        )
    }
}