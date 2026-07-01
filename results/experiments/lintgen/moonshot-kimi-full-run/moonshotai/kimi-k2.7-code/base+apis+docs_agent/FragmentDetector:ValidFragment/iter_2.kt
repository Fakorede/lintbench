package com.android.tools.lint.checks

import com.android.SdkConstants
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
        SdkConstants.CLASS_FRAGMENT,
        SdkConstants.ANDROID_SUPPORT_V4_APP_FRAGMENT,
        SdkConstants.ANDROIDX_FRAGMENT_APP_FRAGMENT
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isInterface || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return
        }

        if (declaration is UAnonymousClass) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment should provide a default constructor (a public no-arg constructor)"
            )
            return
        }

        if (!declaration.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragment ${declaration.name} is not public so it cannot be instantiated"
            )
            return
        }

        val containingClass = declaration.containingClass
        if (containingClass != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragment ${declaration.name} must be a public static class to be properly recreated from instance state"
            )
            return
        }

        val constructors = declaration.constructors
        if (constructors.isEmpty()) {
            return
        }

        var hasDefaultConstructor = false
        for (constructor in constructors) {
            if (constructor.parameterList.parametersCount == 0 &&
                constructor.hasModifierProperty(PsiModifier.PUBLIC)
            ) {
                hasDefaultConstructor = true
                break
            }
        }

        if (!hasDefaultConstructor) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragment ${declaration.name} must provide a default constructor (a public no-arg constructor)"
            )
            return
        }

        if (context.evaluator.extendsClass(
                declaration,
                SdkConstants.ANDROIDX_FRAGMENT_APP_FRAGMENT,
                false
            )
        ) {
            return
        }

        for (constructor in constructors) {
            if (constructor.parameterList.parametersCount != 0) {
                context.report(
                    ISSUE,
                    constructor,
                    context.getLocation(constructor),
                    "Avoid non-default constructors in fragments: use a default constructor plus Fragment#setArguments(Bundle) instead"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                From the Fragment documentation:
                Every fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with `setArguments(Bundle)` and later retrieved by the Fragment with `getArguments()`.

                Note that this is no longer true when you are using `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply any arguments you want (as of version androidx version 1.1).
            """.trimIndent(),
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()",
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