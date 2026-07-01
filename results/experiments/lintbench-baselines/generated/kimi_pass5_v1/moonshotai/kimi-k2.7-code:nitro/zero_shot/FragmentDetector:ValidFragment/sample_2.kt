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
        private const val FRAGMENT_CLS = "android.app.Fragment"

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty constructor, and must be public and not an
                inner class, so it can be instantiated by the framework when restoring its
                activity's state.
            """.trimIndent(),
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun applicableSuperClasses(): List<String> = listOf(FRAGMENT_CLS)

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isAbstract ||
            declaration.isInterface ||
            declaration.isEnum ||
            declaration.isAnnotationType ||
            declaration.name == null
        ) {
            return
        }

        val modifierList = declaration.modifierList ?: return
        if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragment classes should be public so they can be instantiated by the framework"
            )
            return
        }

        if (declaration.containingClass != null &&
            !modifierList.hasModifierProperty(PsiModifier.STATIC)
        ) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragment inner classes should be static so they can be instantiated by the framework"
            )
            return
        }

        val constructors = declaration.constructors
        if (constructors.isEmpty()) {
            return
        }

        val hasPublicEmptyConstructor = constructors.any { constructor ->
            constructor.modifierList?.hasModifierProperty(PsiModifier.PUBLIC) == true &&
                constructor.parameterList.parametersCount == 0
        }

        if (!hasPublicEmptyConstructor) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Fragment is missing a public empty constructor"
            )
        }
    }
}