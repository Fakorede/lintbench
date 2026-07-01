package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UElementHandler
import java.util.EnumSet

class FragmentDetector : Detector(), UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                if (node.isAbstract) return
                val qualifiedName = node.qualifiedName ?: return
                if (qualifiedName == "android.app.Fragment") return

                val evaluator = context.evaluator
                if (!evaluator.extendsClass(node, "android.app.Fragment", false)) return

                val constructors = node.constructors
                if (constructors.isEmpty()) return

                val hasEmptyConstructor = constructors.any { it.parameterList.parameters.isEmpty() }
                val parameterizedConstructors = constructors.filter { it.parameterList.parameters.isNotEmpty() }

                if (!hasEmptyConstructor) {
                    context.report(
                        ISSUE,
                        node,
                        context.getNameLocation(node),
                        "Fragment must have a public empty constructor"
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
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that \
                subclasses do not have other constructors with parameters, since these \
                constructors will not be called when the fragment is re-instantiated; \
                instead, arguments can be supplied by the caller with `setArguments(Bundle)` \
                and later retrieved by the Fragment with `getArguments()`.

                Note that this is no longer true when you are using \
                `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply \
                any arguments you want (as of version androidx version 1.1).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                FragmentDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}