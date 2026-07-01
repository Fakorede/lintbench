package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UAnonymousClass

class FragmentDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that \
                subclasses do not have other constructors with parameters, since these \
                constructors will not be called when the fragment is re-instantiated; \
                instead, arguments can be supplied by the caller with `setArguments(Bundle)` \
                and later retrieved by the Fragment with `getArguments()`.
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

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "android.app.Fragment",
            "android.support.v4.app.Fragment",
            "androidx.fragment.app.Fragment"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (declaration.isInterface || evaluator.isAbstract(declaration)) {
            return
        }

        val isAnonymous = declaration is UAnonymousClass || declaration.name == null
        if (isAnonymous) {
            context.report(
                ISSUE,
                declaration,
                context.getLocation(declaration),
                "This fragment class should be public"
            )
            return
        }

        val containingClass = declaration.containingClass
        if (containingClass != null && !evaluator.isStatic(declaration)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public and static"
            )
            return
        }

        if (!evaluator.isPublic(declaration)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public"
            )
            return
        }

        val constructors = declaration.constructors
        if (constructors.isNotEmpty()) {
            var hasEmptyConstructor = false
            var hasPublicEmptyConstructor = false
            for (constructor in constructors) {
                if (constructor.parameterList.parametersCount == 0) {
                    hasEmptyConstructor = true
                    if (evaluator.isPublic(constructor)) {
                        hasPublicEmptyConstructor = true
                    }
                } else {
                    context.report(
                        ISSUE,
                        constructor,
                        context.getNameLocation(constructor),
                        "Avoid non-default constructors in fragments: use default constructor plus Fragment#setArguments(Bundle) instead"
                    )
                }
            }

            if (!hasEmptyConstructor) {
                context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This fragment should provide a default constructor (a public constructor with no arguments)"
                )
            } else if (!hasPublicEmptyConstructor) {
                context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "The default constructor for this fragment should be public"
                )
            }
        }
    }
}