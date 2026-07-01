package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElementHandler

class FragmentDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val evaluator = context.evaluator

                val isAndroidFragment = evaluator.extendsClass(node, "android.app.Fragment", false)
                val isSupportFragment = evaluator.extendsClass(node, "android.support.v4.app.Fragment", false)
                val isAndroidXFragment = evaluator.extendsClass(node, "androidx.fragment.app.Fragment", false)

                if (!isAndroidFragment && !isSupportFragment) return
                if (isAndroidXFragment) return
                if (evaluator.isAbstract(node)) return

                val constructors = node.constructors
                // Kotlin classes without explicit constructors have an implicit public empty constructor
                val hasEmptyConstructor = constructors.isEmpty() || constructors.any { it.uastParameters.isEmpty() }
                val parameterizedConstructors = constructors.filter { it.uastParameters.isNotEmpty() }

                if (!hasEmptyConstructor) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "This fragment should provide a default constructor (a public constructor with no arguments)"
                    )
                }

                for (constructor in parameterizedConstructors) {
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

                Reference documentation:
                  - https://developer.android.com/reference/android/app/Fragment.html#Fragment()
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