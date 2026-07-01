package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler

class FragmentDetector : Detector(), UastScanner {

    companion object {
        private const val ANDROID_APP_FRAGMENT = "android.app.Fragment"

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

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (node.name == null || node.isInterface || node.isEnum || node.isAbstract) {
                    return
                }

                val evaluator = context.evaluator
                if (!evaluator.extendsClass(node, ANDROID_APP_FRAGMENT, false)) {
                    return
                }

                val constructors = node.constructors
                if (constructors.isEmpty()) {
                    return
                }

                var hasPublicEmptyConstructor = false

                for (constructor in constructors) {
                    if (constructor.uastParameters.isEmpty()) {
                        if (evaluator.isPublic(constructor)) {
                            hasPublicEmptyConstructor = true
                        }
                    } else {
                        context.report(
                            ISSUE,
                            constructor,
                            context.getLocation(constructor),
                            "Avoid non-empty constructors in fragments; use `setArguments(Bundle)` instead"
                        )
                    }
                }

                if (!hasPublicEmptyConstructor) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Fragment must have a public empty constructor"
                    )
                }
            }
        }
    }
}