package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

class FragmentDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (node.isAbstract) return

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