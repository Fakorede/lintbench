package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class FragmentDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (!evaluator.extendsClass(declaration, "android.app.Fragment", false)) {
            return
        }

        if (declaration.isAbstract) {
            return
        }

        val constructors = declaration.methods.filter { it.isConstructor }
        var hasPublicEmptyConstructor = false

        for (constructor in constructors) {
            if (constructor.uastParameters.isEmpty()) {
                if (evaluator.isPublic(constructor)) {
                    hasPublicEmptyConstructor = true
                }
            } else {
                context.report(
                    ISSUE,
                    context.getLocation(constructor),
                    "Avoid non-default constructors in fragments: use a default constructor plus `Fragment#setArguments(Bundle)` instead"
                )
            }
        }

        if (constructors.isNotEmpty() && !hasPublicEmptyConstructor) {
            context.report(
                ISSUE,
                context.getNameLocation(declaration),
                "Fragment must have a public empty constructor"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "ValidFragment",
            "Fragment not instantiable",
            "From the Fragment documentation:\n" +
            "**Every** fragment must have an empty constructor, so it can be instantiated " +
            "when restoring its activity's state. It is strongly recommended that subclasses " +
            "do not have other constructors with parameters, since these constructors will " +
            "not be called when the fragment is re-instantiated; instead, arguments can be " +
            "supplied by the caller with `setArguments(Bundle)` and later retrieved by the " +
            "Fragment with `getArguments()`.\n\n" +
            "Note that this is no longer true when you are using " +
            "`androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply " +
            "any arguments you want (as of version androidx version 1.1).",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}