package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

class FragmentDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (!context.evaluator.extendsClass(node, "android.app.Fragment", false)) {
                    return
                }

                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return
                }

                val constructors = node.methods.filter { it.isConstructor }
                if (constructors.isEmpty()) {
                    return
                }

                var hasPublicEmptyConstructor = false

                for (constructor in constructors) {
                    val paramCount = constructor.parameterList.parametersCount
                    if (paramCount == 0) {
                        if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                            hasPublicEmptyConstructor = true
                        } else {
                            context.report(
                                ISSUE,
                                context.getLocation(constructor as UElement),
                                "Fragment empty constructor must be public"
                            )
                        }
                    } else {
                        context.report(
                            ISSUE,
                            context.getLocation(constructor as UElement),
                            "Avoid non-default constructors in fragments: use a default constructor plus `Fragment#setArguments(Bundle)` instead"
                        )
                    }
                }

                if (!hasPublicEmptyConstructor) {
                    context.report(
                        ISSUE,
                        context.getLocation(node as UElement),
                        "Fragment must have a public empty constructor"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiable",
            explanation = """
                From the Fragment documentation:
                **Every** fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that subclasses \
                do not have other constructors with parameters, since these constructors will \
                not be called when the fragment is re-instantiated; instead, arguments can be \
                supplied by the caller with `setArguments(Bundle)` and later retrieved by the \
                Fragment with `getArguments()`.

                Note that this is no longer true when you are using \
                `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply \
                any arguments you want (as of version androidx version 1.1).
            """.trimIndent(),
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