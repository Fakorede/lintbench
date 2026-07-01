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

        private const val FRAGMENT_CLASS = "android.app.Fragment"
        private const val SUPPORT_FRAGMENT_CLASS = "android.support.v4.app.Fragment"
    }

    override fun getApplicableUastTypes(): List<Class<out org.jetbrains.uast.UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val psiClass = node.javaPsi

                if (psiClass.isInterface || psiClass.isAnnotationType || psiClass.isEnum) {
                    return
                }

                if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return
                }

                val evaluator = context.evaluator
                val isFragment = evaluator.inheritsFrom(psiClass, FRAGMENT_CLASS, false) ||
                        evaluator.inheritsFrom(psiClass, SUPPORT_FRAGMENT_CLASS, false)

                if (!isFragment) {
                    return
                }

                // 1. Must be public
                if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This fragment class should be public"
                    )
                    return
                }

                // 2. If inner class, must be static
                val containingClass = psiClass.containingClass
                if (containingClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "This fragment is an inner class and should be static"
                    )
                    return
                }

                // 3. Must have a public no-arg constructor
                val constructors = psiClass.constructors
                if (constructors.isNotEmpty()) {
                    var hasPublicNoArg = false
                    for (constructor in constructors) {
                        if (constructor.parameterList.parametersCount == 0) {
                            if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                                hasPublicNoArg = true
                                break
                            }
                        }
                    }
                    if (!hasPublicNoArg) {
                        context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            "This fragment should provide a default constructor (a public constructor with no arguments)"
                        )
                    }
                }
            }
        }
    }
}