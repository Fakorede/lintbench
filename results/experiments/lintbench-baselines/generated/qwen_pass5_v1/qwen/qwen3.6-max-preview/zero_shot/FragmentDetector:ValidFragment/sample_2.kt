package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElementHandler

class FragmentDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<UClass>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (node.name == null || node.isInterface || node.isAbstract) return

                val evaluator = context.evaluator
                val isAndroidFragment = evaluator.extendsClass(node, "android.app.Fragment", false)
                val isSupportFragment = evaluator.extendsClass(node, "android.support.v4.app.Fragment", false)
                val isAndroidxFragment = evaluator.extendsClass(node, "androidx.fragment.app.Fragment", false)

                if (isAndroidxFragment) return
                if (!isAndroidFragment && !isSupportFragment) return

                val constructors = node.methods.filter { it.isConstructor }
                if (constructors.isEmpty()) return

                val hasEmptyConstructor = constructors.any { it.parameterList.parameters.isEmpty() }
                if (!hasEmptyConstructor) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "This fragment should provide a default constructor (a public constructor with no arguments)"
                    )
                }

                for (constructor in constructors) {
                    if (constructor.parameterList.parameters.isNotEmpty()) {
                        context.report(
                            ISSUE,
                            constructor,
                            context.getLocation(constructor),
                            "Avoid non-default constructors in fragments: use a default constructor plus `Fragment#setArguments(Bundle)` instead"
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