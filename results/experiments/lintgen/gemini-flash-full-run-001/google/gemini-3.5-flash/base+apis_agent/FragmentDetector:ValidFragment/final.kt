package com.android.tools.lint.checks

import com.android.SdkConstants
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
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Node

class FragmentDetector : Detector(), SourceCodeScanner {

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

    override fun applicableSuperClasses(): List<String> {
        return listOf(
            SdkConstants.CLASS_FRAGMENT,
            "android.support.v4.app.Fragment",
            "androidx.fragment.app.Fragment"
        )
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val evaluator = context.evaluator
        if (evaluator.isAbstract(declaration)) {
            return
        }

        val isAnonymous = declaration is UAnonymousClass || declaration.name == null
        if (isAnonymous) {
            val message = "Fragments should not be anonymous"
            context.report(ISSUE, declaration, context.getLocation(declaration as UElement), message)
            return
        }

        if (!evaluator.isPublic(declaration)) {
            val message = "This fragment class should be public"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            return
        }

        if (declaration.containingClass != null && !evaluator.isStatic(declaration)) {
            val message = "This fragment class should be static"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            return
        }

        val constructors = declaration.constructors
        if (constructors.isEmpty()) {
            return
        }

        var hasEmptyConstructor = false
        var hasPublicEmptyConstructor = false

        for (constructor in constructors) {
            val parametersCount = constructor.parameterList.parametersCount
            if (parametersCount == 0) {
                hasEmptyConstructor = true
                if (evaluator.isPublic(constructor)) {
                    hasPublicEmptyConstructor = true
                }
            }
        }

        if (!hasEmptyConstructor) {
            val message = "This fragment class should be public, static and have a public empty constructor"
            context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
        } else if (!hasPublicEmptyConstructor) {
            val emptyConstructor = constructors.firstOrNull { it.parameterList.parametersCount == 0 }
            val message = "The default constructor for this fragment must be public"
            if (emptyConstructor != null) {
                context.report(ISSUE, emptyConstructor, context.getNameLocation(emptyConstructor), message)
            } else {
                context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
            }
        }

        for (constructor in constructors) {
            if (constructor.parameterList.parametersCount > 0) {
                val message = "Avoid non-default constructors in fragments: use a default constructor plus Fragment#setArguments(Bundle) instead"
                context.report(ISSUE, constructor, context.getNameLocation(constructor), message)
            }
        }
    }
}