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
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.uast.UClass

class FragmentDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "android.app.Fragment",
            "android.support.v4.app.Fragment",
            "androidx.fragment.app.Fragment"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (evaluator.isAbstract(declaration) || evaluator.isInterface(declaration)) {
            return
        }

        // Must be public
        if (!evaluator.isPublic(declaration)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public"
            )
            return
        }

        // Must not be a non-static inner class
        if (declaration.containingClass != null && !evaluator.isStatic(declaration)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public and static (or a top-level class)"
            )
            return
        }

        // Must have a public empty constructor
        val constructors = declaration.constructors
        if (constructors.isEmpty()) {
            // Default constructor is generated and public
            return
        }

        var hasPublicNoArgConstructor = false
        var hasConstructor = false

        for (constructor in constructors) {
            hasConstructor = true
            if (constructor.parameterList.parametersCount == 0) {
                if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                    hasPublicNoArgConstructor = true
                    break
                }
            }
        }

        if (hasConstructor && !hasPublicNoArgConstructor) {
            // Check if there is a public constructor where all parameters have default values (Kotlin)
            val hasKotlinDefaultConstructor = constructors.any { constructor ->
                if (!constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                    return@any false
                }
                val uMethod = context.uastContext.getMethod(constructor)
                uMethod != null && uMethod.uastParameters.isNotEmpty() && uMethod.uastParameters.all { parameter ->
                    val sourcePsi = parameter.sourcePsi
                    sourcePsi is KtParameter && sourcePsi.hasDefaultValue()
                }
            }

            if (!hasKotlinDefaultConstructor) {
                context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This fragment must be public and have an empty constructor"
                )
            }
        }
    }

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
            implementation = Implementation(
                FragmentDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}