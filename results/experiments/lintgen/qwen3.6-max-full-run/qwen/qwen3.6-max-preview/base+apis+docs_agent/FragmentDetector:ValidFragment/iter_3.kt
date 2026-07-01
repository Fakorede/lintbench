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
        @JvmField
        val ISSUE = Issue.create(
            "ValidFragment",
            "Fragment not instantiatable",
            "From the Fragment documentation:\n" +
            "**Every** fragment must have an empty constructor, so it can be instantiated " +
            "when restoring its activity's state. It is strongly recommended that subclasses " +
            "do not have other constructors with parameters, since these constructors will " +
            "not be called when the fragment is re-instantiated; instead, arguments can be " +
            "supplied by the caller with `setArguments(Bundle)` and later retrieved by the " +
            "Fragment with `getArguments()`.\n\n" +
            "Note that this is no longer true when you are using " +
            "`androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply " +
            "any arguments you want (as of version androidx version 1.1).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }

    override fun applicableSuperClasses(): List<String>? {
        return listOf("android.app.Fragment")
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.name == null) return

        for (method in declaration.methods) {
            if (method.isConstructor && method.uastParameters.isNotEmpty()) {
                context.report(
                    ISSUE,
                    context.getNameLocation(method),
                    "Avoid non-default constructors in fragments: use a default constructor plus `Fragment#setArguments(Bundle)` instead"
                )
            }
        }
    }
}