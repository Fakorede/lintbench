package com.android.tools.lint.checks

import com.android.SdkConstants.CLASS_FRAGMENT
import com.android.SdkConstants.CLASS_V4_FRAGMENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.util.EnumSet

/**
 * Checks that Fragment subclasses can be instantiated via reflection,
 * which requires a public no-argument constructor.
 */
class FragmentDetector : Detector(), ClassScanner {

    companion object {
        private const val ANDROIDX_FRAGMENT_CLASS = "androidx/fragment/app/Fragment"

        val ISSUE: Issue = Issue.create(
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
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()",
            implementation = Implementation(
                FragmentDetector::class.java,
                EnumSet.of(Scope.CLASS_FILE)
            )
        )

        /** The internal class name for android.app.Fragment */
        private val FRAGMENT_CLASS = CLASS_FRAGMENT.replace('.', '/')

        /** The internal class name for android.support.v4.app.Fragment */
        private val FRAGMENT_V4_CLASS = CLASS_V4_FRAGMENT.replace('.', '/')
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(FRAGMENT_CLASS, FRAGMENT_V4_CLASS, ANDROIDX_FRAGMENT_CLASS)
    }

    override fun checkClass(context: ClassContext, classNode: ClassNode) {
        // Skip abstract classes - they don't need to be instantiated directly
        if (classNode.access and Opcodes.ACC_ABSTRACT != 0) {
            return
        }

        // Skip anonymous classes
        if (classNode.name.contains('$')) {
            // Check if it's an anonymous class (no simple name)
            val simpleName = classNode.name.substringAfterLast('$')
            if (simpleName.all { it.isDigit() }) {
                // Anonymous inner class - skip
                return
            }
        }

        // For androidx fragments, we don't flag issues since FragmentFactory handles this
        if (isAndroidXFragment(context, classNode)) {
            return
        }

        // Check if the class is a non-static inner class
        if (isNonStaticInnerClass(classNode)) {
            context.report(
                ISSUE,
                context.getLocation(classNode),
                "This fragment inner class should be static (${classNode.name.replace('/', '.').replace('$', '.')})"
            )
            return
        }

        // Look for a public no-argument constructor
        val methods = classNode.methods
        var hasNoArgConstructor = false
        var hasConstructors = false

        if (methods != null) {
            for (method in methods) {
                val methodNode = method as MethodNode
                if (methodNode.name == "<init>") {
                    hasConstructors = true
                    // Check if it's a no-arg constructor
                    if (methodNode.desc == "()V") {
                        // Check if it's public
                        if (methodNode.access and Opcodes.ACC_PUBLIC != 0) {
                            hasNoArgConstructor = true
                        }
                    }
                }
            }
        }

        // If there are no constructors defined, the default no-arg constructor is used
        if (!hasConstructors) {
            // Default constructor exists and is public (if class is public)
            return
        }

        if (!hasNoArgConstructor) {
            context.report(
                ISSUE,
                context.getLocation(classNode),
                "This fragment should provide a default constructor (a public constructor with no arguments) (`${classNode.name.replace('/', '.').replace('$', '.')}`)"
            )
        }
    }

    /**
     * Checks if the class is a non-static inner class (which cannot be
     * instantiated without an outer class instance).
     */
    private fun isNonStaticInnerClass(classNode: ClassNode): Boolean {
        val name = classNode.name
        if (!name.contains('$')) {
            return false
        }

        // If the class is not static, it's a non-static inner class
        return classNode.access and Opcodes.ACC_STATIC == 0 &&
                classNode.outerClass != null
    }

    /**
     * Determines if this class extends androidx.fragment.app.Fragment
     * (directly or indirectly). If so, we skip the check since
     * FragmentFactory handles instantiation.
     */
    private fun isAndroidXFragment(context: ClassContext, classNode: ClassNode): Boolean {
        // Check superclass hierarchy
        var superName = classNode.superName
        val visited = mutableSetOf<String>()

        while (superName != null && superName != "java/lang/Object") {
            if (visited.contains(superName)) break
            visited.add(superName)

            if (superName == ANDROIDX_FRAGMENT_CLASS) {
                return true
            }

            // Try to get the super class node
            val superClassNode = context.driver.findClass(context, superName, 0)
            superName = superClassNode?.superName ?: break
        }

        return false
    }
}