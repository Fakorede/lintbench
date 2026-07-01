package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElementHandler

class FragmentDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            if (node.isAbstract || node.isInterface || node.isEnum || node.isAnnotationType) return

            val isFragment = FRAGMENT_CLASSES.any { fragmentClass ->
                context.evaluator.extendsClass(node.javaPsi, fragmentClass, false)
            }
            if (!isFragment) return

            // androidx.fragment.app.Fragment supports FragmentFactory as of version 1.1.0
            if (context.evaluator.extendsClass(node.javaPsi, ANDROIDX_FRAGMENT, false)) return

            val constructors = node.methods.filter { it.isConstructor }
            if (constructors.isEmpty()) return

            val publicNoArgConstructor = constructors.firstOrNull { constructor ->
                constructor.parameterList.parametersCount == 0 &&
                    constructor.hasModifierProperty(PsiModifier.PUBLIC)
            }

            if (publicNoArgConstructor == null) {
                val target = constructors.firstOrNull {
                    it.parameterList.parametersCount == 0
                } ?: node
                context.report(
                    ISSUE,
                    target,
                    context.getNameLocation(target),
                    "This fragment class should provide a public no-arg constructor so it can be re-instantiated by the framework"
                )
            }
        }
    }

    companion object {
        private const val ANDROID_APP_FRAGMENT = "android.app.Fragment"
        private const val ANDROID_SUPPORT_FRAGMENT = "android.support.v4.app.Fragment"
        private const val ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment"

        private val FRAGMENT_CLASSES = listOf(
            ANDROID_APP_FRAGMENT,
            ANDROID_SUPPORT_FRAGMENT,
            ANDROIDX_FRAGMENT
        )

        @JvmField
        val ISSUE = Issue.create(
            "ValidFragment",
            "Fragment not instantiatable",
            """
                Every fragment must have an empty constructor, so it can be instantiated when restoring its activity's state. It is strongly recommended that subclasses do not have other constructors with parameters, since these constructors will not be called when the fragment is re-instantiated; instead, arguments can be supplied by the caller with setArguments(Bundle) and later retrieved by the Fragment with getArguments().

                Note that this is no longer true when you are using androidx.fragment.app.Fragment; with the FragmentFactory you can supply any arguments you want (as of version androidx version 1.1).
            """.trimIndent(),
            Category.CORRECTNESS,
            7,
            Severity.ERROR,
            Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}