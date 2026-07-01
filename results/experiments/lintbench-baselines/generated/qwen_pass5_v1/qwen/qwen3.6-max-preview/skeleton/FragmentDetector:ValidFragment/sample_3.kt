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

class FragmentDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            FragmentDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = "Every fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with setArguments(Bundle) and later retrieved by the Fragment with getArguments().",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun applicableSuperClasses(): List<String>? = listOf("android.app.Fragment")

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isAbstract) return

        if (!declaration.isPublic) {
            context.report(
                ISSUE,
                declaration,
                context.getLocation(declaration),
                "Fragment must be public"
            )
            return
        }

        if (declaration.containingClass != null && !declaration.isStatic) {
            context.report(
                ISSUE,
                declaration,
                context.getLocation(declaration),
                "Fragment must be a static inner class"
            )
            return
        }

        val constructors = declaration.methods.filter { it.isConstructor }
        if (constructors.isNotEmpty()) {
            val hasPublicEmptyConstructor = constructors.any { ctor ->
                ctor.isPublic && ctor.uastParameters.isEmpty()
            }
            if (!hasPublicEmptyConstructor) {
                context.report(
                    ISSUE,
                    declaration,
                    context.getLocation(declaration),
                    "Fragment must have a public default constructor"
                )
            }
        }
    }
}