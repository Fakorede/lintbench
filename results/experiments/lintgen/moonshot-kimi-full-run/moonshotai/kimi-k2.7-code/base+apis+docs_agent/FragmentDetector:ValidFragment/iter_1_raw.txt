package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UClass

class FragmentDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String> = listOf(
        ANDROID_APP_FRAGMENT,
        SUPPORT_FRAGMENT,
        ANDROIDX_FRAGMENT
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isInterface) return
        if (declaration is UAnonymousClass) return
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) return

        if (!declaration.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragment classes must be public so they can be instantiated by the framework"
            )
            return
        }

        val containingClass = declaration.containingClass
        if (containingClass != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be static (declarations of inner classes should be static to prevent memory leaks)"
            )
            return
        }

        val constructors = declaration.constructors
        if (constructors.isEmpty()) {
            return
        }

        var hasPublicNoArg = false
        val nonDefaultConstructors = mutableListOf<PsiMethod>()

        for (constructor in constructors) {
            if (constructor.parameterList.parametersCount == 0) {
                if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                    hasPublicNoArg = true
                }
            } else {
                nonDefaultConstructors.add(constructor)
            }
        }

        if (!hasPublicNoArg) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragments must have a public no-arg constructor so they can be re-instantiated by the framework"
            )
            return
        }

        val isAndroidX = context.evaluator.extendsClass(declaration, ANDROIDX_FRAGMENT, false)
        if (nonDefaultConstructors.isNotEmpty() && !isAndroidX) {
            for (constructor in nonDefaultConstructors) {
                context.report(
                    ISSUE,
                    constructor,
                    context.getLocation(constructor),
                    "Avoid non-default constructors in fragments: use a static factory method or setArguments(Bundle) instead"
                )
            }
        }
    }

    companion object {
        private const val ANDROID_APP_FRAGMENT = "android.app.Fragment"
        private const val SUPPORT_FRAGMENT = "android.support.v4.app.Fragment"
        private const val ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                From the Fragment documentation:
                **Every** fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with `setArguments(Bundle)` and later retrieved by the Fragment with `getArguments()`.

                Note that this is no longer true when you are using `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply any arguments you want (as of version androidx version 1.1).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(
                FragmentDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}