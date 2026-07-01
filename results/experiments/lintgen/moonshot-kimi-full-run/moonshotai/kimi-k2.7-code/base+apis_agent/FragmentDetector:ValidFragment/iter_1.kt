package com.android.tools.lint.checks

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

                Note that this is no longer true when you are using `androidx.fragment.app.Fragment`;
                with the `FragmentFactory` you can supply any arguments you want.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                FragmentDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE)
            )
        )
    }

    override fun applicableSuperClasses(): List<String>? = listOf(
        "android.app.Fragment",
        "android.support.v4.app.Fragment"
    )

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val constructors = declaration.constructors
        if (constructors.isEmpty()) {
            return
        }

        val hasNoArgConstructor = constructors.any { it.parameterList.parameters.isEmpty() }
        if (!hasNoArgConstructor) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This fragment should provide a no-arg constructor so it can be re-instantiated by the system"
            )
        }

        for (constructor in constructors) {
            if (constructor.parameterList.parameters.isNotEmpty()) {
                context.report(
                    ISSUE,
                    constructor,
                    context.getNameLocation(constructor),
                    "Avoid passing arguments to fragment constructors; use `setArguments(Bundle)` instead"
                )
            }
        }
    }
}