package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class FragmentDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (context.evaluator.isAbstract(node)) return

                val evaluator = context.evaluator
                val isFragment = evaluator.extendsClass(node, "android.app.Fragment", false) ||
                                 evaluator.extendsClass(node, "android.support.v4.app.Fragment", false)
                if (!isFragment) return

                val constructors = node.methods.filter { it.isConstructor }
                if (constructors.isEmpty()) return

                val hasPublicEmpty = constructors.any { ctor ->
                    ctor.parameterList.parametersCount == 0 && evaluator.isPublic(ctor)
                }

                if (!hasPublicEmpty) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Fragment must have a public empty constructor"
                    )
                }

                for (ctor in constructors) {
                    if (ctor.parameterList.parametersCount > 0) {
                        context.report(
                            ISSUE,
                            ctor,
                            context.getLocation(ctor),
                            "Avoid non-empty constructors in fragments; use `setArguments(Bundle)` instead"
                        )
                    }
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                From the Fragment documentation:
                **Every** fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with `setArguments(Bundle)` and later retrieved by the Fragment with `getArguments()`.

                Note that this is no longer true when you are using `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply any arguments you want (as of version androidx version 1.1).
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