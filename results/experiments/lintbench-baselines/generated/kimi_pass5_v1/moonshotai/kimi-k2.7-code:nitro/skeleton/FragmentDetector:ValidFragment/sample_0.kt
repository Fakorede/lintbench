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
import org.jetbrains.uast.UMethod

class FragmentDetector : Detector(), SourceCodeScanner {

    companion object {
        private const val ANDROID_APP_FRAGMENT = "android.app.Fragment"
        private const val ANDROID_SUPPORT_FRAGMENT = "android.support.v4.app.Fragment"
        private const val ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment"

        private val IMPLEMENTATION = Implementation(
            FragmentDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty (no-argument) constructor so the framework
                can re-instantiate it when restoring an activity's state. It is strongly
                recommended that subclasses do not have other constructors with parameters;
                instead, arguments can be supplied with `setArguments(Bundle)` and later
                retrieved with `getArguments()`.

                Note: this requirement does not apply to `androidx.fragment.app.Fragment`
                when using `FragmentFactory` (available in androidx 1.1+).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String> =
        listOf(ANDROID_APP_FRAGMENT, ANDROID_SUPPORT_FRAGMENT, ANDROIDX_FRAGMENT)

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.name == null) return
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) return

        val constructors = declaration.constructors
        if (constructors.isEmpty()) return

        if (constructors.any { isValidFragmentConstructor(it) }) return

        context.report(
            ISSUE,
            declaration,
            context.getNameLocation(declaration),
            "This fragment should provide a public no-arg constructor"
        )
    }

    private fun isValidFragmentConstructor(method: UMethod): Boolean =
        method.uastParameters.isEmpty() && method.hasModifierProperty(PsiModifier.PUBLIC)
}