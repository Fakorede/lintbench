package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
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

    override fun getApplicableUastTypes(): List<Class<out UClass>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return
                }

                val evaluator = context.evaluator
                if (evaluator.extendsClass(node, ANDROIDX_FRAGMENT, false)) {
                    return
                }

                if (!evaluator.extendsClass(node, ANDROID_APP_FRAGMENT, false)
                    && !evaluator.extendsClass(node, SUPPORT_FRAGMENT, false)) {
                    return
                }

                if (node.name == null) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Fragment subclasses cannot be anonymous inner classes"
                    )
                    return
                }

                if (node.containingClass != null && !node.hasModifierProperty(PsiModifier.STATIC)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This fragment inner class should be static (${node.name})"
                    )
                    return
                }

                if (node.isEnum) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This fragment should not be an enum (declared in ${node.name})"
                    )
                    return
                }

                val constructors = node.constructors
                val hasPublicDefaultConstructor = if (constructors.isEmpty()) {
                    true
                } else {
                    constructors.any {
                        it.parameterList.parametersCount == 0
                                && it.hasModifierProperty(PsiModifier.PUBLIC)
                    }
                }

                if (!hasPublicDefaultConstructor) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This fragment should provide a public empty constructor (a public constructor with no arguments) as per the Fragment documentation"
                    )
                }

                for (constructor in constructors) {
                    if (constructor.parameterList.parametersCount > 0) {
                        context.report(
                            ISSUE,
                            constructor,
                            context.getLocation(constructor),
                            "Avoid non-default constructors in fragments: use a default constructor plus Fragment#setArguments(Bundle) instead"
                        )
                    }
                }
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