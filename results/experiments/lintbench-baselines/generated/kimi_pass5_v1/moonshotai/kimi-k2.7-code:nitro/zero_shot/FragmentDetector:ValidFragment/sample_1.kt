package com.android.tools.lint.checks

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import org.jetbrains.uast.UMethod

class FragmentDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUElementTypes(): List<Class<out UElement>> =
        listOf(UClass::class.java)

    override fun createUElementHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                this@FragmentDetector.visitClass(context, node)
            }
        }

    private fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isEnum || declaration.name == null) {
            return
        }

        if (context.evaluator.isAbstract(declaration)) {
            return
        }

        if (!isFragmentClass(context.evaluator, declaration)) {
            return
        }

        if (context.evaluator.extendsClass(declaration, ANDROIDX_FRAGMENT, false)) {
            return
        }

        if (!context.evaluator.isPublic(declaration)) {
            report(
                context,
                declaration,
                "Fragments must be public so the framework can re-instantiate them"
            )
            return
        }

        if (declaration.containingClass != null
            && !context.evaluator.isStatic(declaration)
        ) {
            report(
                context,
                declaration,
                "Fragment inner classes must be static so the framework can re-instantiate them"
            )
            return
        }

        val constructors = declaration.constructors
        val noArgConstructor = constructors.find { it.uastParameters.isEmpty() }

        if (noArgConstructor == null) {
            report(
                context,
                declaration,
                "Fragments must have a public no-arg constructor so the framework can re-instantiate them"
            )
        } else if (!context.evaluator.isPublic(noArgConstructor)) {
            report(
                context,
                noArgConstructor,
                "The fragment's no-arg constructor must be public"
            )
        }
    }

    private fun isFragmentClass(evaluator: JavaEvaluator, declaration: UClass): Boolean =
        evaluator.extendsClass(declaration, ANDROID_APP_FRAGMENT, false)
            || evaluator.extendsClass(declaration, ANDROID_SUPPORT_FRAGMENT, false)

    private fun report(context: JavaContext, node: UClass, message: String) {
        context.report(ISSUE, node, message)
    }

    private fun report(context: JavaContext, node: UMethod, message: String) {
        context.report(ISSUE, node, message)
    }

    companion object {
        private const val ANDROID_APP_FRAGMENT = "android.app.Fragment"
        private const val ANDROID_SUPPORT_FRAGMENT = "android.support.v4.app.Fragment"
        private const val ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty (no-argument) constructor so that the framework
                can instantiate it when restoring the activity's state. It is strongly recommended
                that subclasses do not define constructors with parameters; instead, arguments should
                be supplied by the caller with <code>setArguments(Bundle)</code> and later retrieved
                with <code>getArguments()</code>.

                This requirement does not apply to <code>androidx.fragment.app.Fragment</code> when
                used with <code>FragmentFactory</code> (available in androidx 1.1+).
            """,
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                FragmentDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}