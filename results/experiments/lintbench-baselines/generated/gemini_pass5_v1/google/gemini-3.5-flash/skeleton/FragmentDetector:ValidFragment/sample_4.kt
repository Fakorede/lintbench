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
        private val IMPLEMENTATION = Implementation(
            FragmentDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

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
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf(
            "android.app.Fragment",
            "android.support.v4.app.Fragment"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return
        }

        if (declaration.isInterface) {
            return
        }

        val isPublic = declaration.hasModifierProperty(PsiModifier.PUBLIC)
        if (!isPublic) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be public"
            )
            return
        }

        val containingClass = declaration.containingClass
        if (containingClass != null && !declaration.hasModifierProperty(PsiModifier.STATIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment class should be static"
            )
            return
        }

        val constructors = declaration.constructors
        if (constructors.isNotEmpty()) {
            var hasPublicEmptyConstructor = false
            for (constructor in constructors) {
                val isConstructorPublic = constructor.hasModifierProperty(PsiModifier.PUBLIC)
                val hasNoParameters = constructor.parameterList.parametersCount == 0
                if (isConstructorPublic && hasNoParameters) {
                    hasPublicEmptyConstructor = true
                    break
                }
            }

            if (!hasPublicEmptyConstructor) {
                context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This fragment must have a public empty constructor"
                )
            }
        }
    }
}