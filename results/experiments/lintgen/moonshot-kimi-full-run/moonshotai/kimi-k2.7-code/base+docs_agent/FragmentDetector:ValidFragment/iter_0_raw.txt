package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.JavaEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass

class FragmentDetector : Detector(), SourceCodeScanner {

    override fun applicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return
                }

                val evaluator = context.evaluator

                // androidx.fragment.app.Fragment supports FragmentFactory; this check does not apply.
                if (evaluator.extendsClass(node, ANDROIDX_FRAGMENT, true)) {
                    return
                }

                if (!isFragmentClass(node, evaluator)) {
                    return
                }

                val constructors = node.constructors
                if (constructors.isEmpty()) {
                    return
                }

                val hasDefaultConstructor = constructors.any { it.uastParameters.isEmpty() }

                if (!hasDefaultConstructor) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This fragment should provide a default constructor (a public constructor with no arguments) as per the Fragment documentation"
                    )
                }

                for (constructor in constructors) {
                    if (constructor.uastParameters.isNotEmpty()) {
                        context.report(
                            ISSUE,
                            constructor,
                            context.getLocation(constructor),
                            "Avoid non-default constructors in fragments: use a default constructor plus `Fragment#setArguments(Bundle)` instead"
                        )
                    }
                }
            }
        }
    }

    private fun isFragmentClass(node: UClass, evaluator: JavaEvaluator): Boolean {
        return evaluator.extendsClass(node, "android.app.Fragment", true)
                || evaluator.extendsClass(node, "android.support.v4.app.Fragment", true)
    }

    companion object {
        private const val ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                From the Fragment documentation: Every fragment must have an empty constructor,
                so it can be instantiated when restoring its activity's state. It is strongly
                recommended that subclasses do not have other constructors with parameters, since
                these constructors will not be called when the fragment is re-instantiated; instead,
                arguments can be supplied by the caller with `setArguments(Bundle)` and later
                retrieved by the Fragment with `getArguments()`.

                This check does not apply to `androidx.fragment.app.Fragment`, which can use a
                `FragmentFactory` to supply arguments.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FragmentDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}