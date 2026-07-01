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
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UConstructor

class FragmentDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val VALID_FRAGMENT = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with `setArguments(Bundle)` and later retrieved by the Fragment with `getArguments()`.

                Note that this is no longer true when you are using `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply any arguments you want (as of version androidx version 1.1).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf("android.app.Fragment", "androidx.fragment.app.Fragment")
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT) || declaration.name == null) {
            return
        }

        val constructors: List<UConstructor> = declaration.constructors
        val hasEmptyConstructor = constructors.isEmpty() || constructors.any { ctor ->
            ctor.uastParameters.isEmpty() && !ctor.hasModifierProperty(PsiModifier.PRIVATE)
        }

        if (!hasEmptyConstructor) {
            context.report(
                Incident(
                    VALID_FRAGMENT,
                    declaration,
                    context.getLocation(declaration),
                    "Fragment must have a public empty constructor"
                )
            )
        }

        constructors.filter { ctor ->
            ctor.uastParameters.isNotEmpty() && !ctor.hasModifierProperty(PsiModifier.PRIVATE)
        }.forEach { ctor ->
            context.report(
                Incident(
                    VALID_FRAGMENT,
                    ctor,
                    context.getLocation(ctor),
                    "Avoid non-default constructors in fragments: use a default constructor plus Fragment#setArguments(Bundle) instead"
                )
            )
        }
    }
}