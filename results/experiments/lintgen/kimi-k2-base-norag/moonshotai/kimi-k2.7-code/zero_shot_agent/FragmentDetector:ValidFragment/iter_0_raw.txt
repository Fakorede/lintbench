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
import org.jetbrains.uast.UClass
import java.util.EnumSet

class FragmentDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (isAndroidXFragment(context, node) || !isLegacyFragment(context, node)) {
                    return
                }

                if (context.evaluator.isAbstract(node)) {
                    return
                }

                val constructors = node.constructors
                val hasPublicNoArgConstructor = if (constructors.isEmpty()) {
                    context.evaluator.isPublic(node)
                } else {
                    constructors.any { ctor ->
                        ctor.parameterList.parametersCount == 0 &&
                                context.evaluator.isPublic(ctor)
                    }
                }

                if (!hasPublicNoArgConstructor) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Fragments must have a public empty constructor so they can be instantiated by the framework"
                    )
                }

                for (constructor in constructors) {
                    if (constructor.parameterList.parametersCount > 0) {
                        context.report(
                            ISSUE,
                            constructor,
                            context.getLocation(constructor),
                            "Avoid non-default constructors in fragments: use a default constructor plus `setArguments(Bundle)` instead"
                        )
                    }
                }
            }
        }
    }

    private fun isLegacyFragment(context: JavaContext, node: UClass): Boolean {
        return context.evaluator.extendsClass(node, "android.app.Fragment", false) ||
                context.evaluator.extendsClass(node, "android.support.v4.app.Fragment", false)
    }

    private fun isAndroidXFragment(context: JavaContext, node: UClass): Boolean {
        return context.evaluator.extendsClass(node, "androidx.fragment.app.Fragment", false)
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty constructor, so it can be instantiated
                when restoring its activity's state. It is strongly recommended that subclasses
                do not have other constructors with parameters, since these constructors will
                not be called when the fragment is re-instantiated; instead, arguments can be
                supplied by the caller with `setArguments(Bundle)` and later retrieved by the
                Fragment with `getArguments()`.

                Note that this is no longer true when you are using
                `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply
                any arguments you want (as of version androidx version 1.1).
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                FragmentDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE)
            )
        )
    }
}