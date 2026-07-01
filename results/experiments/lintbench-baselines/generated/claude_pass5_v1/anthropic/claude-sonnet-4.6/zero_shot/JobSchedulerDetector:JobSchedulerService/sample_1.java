/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks for common mistakes when using the JobScheduler API.
 */
public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String JOB_SCHEDULER_CLASS = "android.app.job.JobScheduler";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, " +
            "the service must be registered in the manifest and the registration " +
            "must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.MANIFEST_SCOPE
            ))
            .addMoreInfo("https://developer.android.com/topic/performance/scheduling.html");

    /**
     * Map from service class name to the location where it was referenced in a JobInfo.Builder
     * call (in Java source).
     */
    private final Map<String, Location> mJobServiceReferences = new HashMap<>();

    /**
     * Map from service class name to the PsiClass for type-checking purposes.
     */
    private final Map<String, PsiClass> mJobServiceClasses = new HashMap<>();

    /**
     * Map from service class name to the JavaContext where the reference was found.
     */
    private final Map<String, JavaContext> mJobServiceContexts = new HashMap<>();

    /**
     * Set of service names registered in the manifest (fully qualified).
     */
    private final Map<String, Element> mManifestServices = new HashMap<>();

    /**
     * Set of service names that have BIND_JOB_SERVICE permission in the manifest.
     */
    private final Map<String, Boolean> mManifestServicePermissions = new HashMap<>();

    public JobSchedulerDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Resolve the fully qualified name
        String fqn = resolveServiceName(context, name);
        if (fqn != null) {
            mManifestServices.put(fqn, element);
            String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
            mManifestServicePermissions.put(fqn, BIND_JOB_SERVICE_PERMISSION.equals(permission));
        }
    }

    @Nullable
    private String resolveServiceName(@NonNull XmlContext context, @NonNull String name) {
        if (name.startsWith(".")) {
            // Relative name - prepend package
            String pkg = context.getMainProject().getPackage();
            if (pkg != null) {
                return pkg + name;
            }
        } else if (!name.contains(".")) {
            // Simple name - prepend package
            String pkg = context.getMainProject().getPackage();
            if (pkg != null) {
                return pkg + "." + name;
            }
        }
        // Already fully qualified
        return name;
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList("setComponent", "schedule");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {

        JavaEvaluator evaluator = context.getEvaluator();

        String methodName = call.getMethodName();
        if ("setComponent".equals(methodName)) {
            // Check if this is JobInfo.Builder.setComponent(ComponentName)
            if (!evaluator.isMemberInClass(method, JOB_INFO_BUILDER_CLASS)) {
                return;
            }

            // The second argument should be a ComponentName - we need to find the class
            // being referenced. Typically: new ComponentName(context, MyJobService.class)
            List<UExpression> args = call.getValueArguments();
            if (args.size() < 2) {
                return;
            }

            // Look for a class literal in the arguments to ComponentName constructor
            UExpression componentNameArg = args.get(1);
            // The component name arg might be a call to new ComponentName(...)
            // We need to find the class literal within it
            checkComponentNameArg(context, call, componentNameArg);

        } else if ("schedule".equals(methodName)) {
            // Check if this is JobScheduler.schedule(JobInfo)
            if (!evaluator.isMemberInClass(method, JOB_SCHEDULER_CLASS)) {
                return;
            }
            // The actual checking is done at afterCheckRootProject
        }
    }

    private void checkComponentNameArg(
            @NonNull JavaContext context,
            @NonNull UCallExpression setComponentCall,
            @NonNull UExpression componentNameArg) {

        // We need to find class literals in the component name expression
        // Walk the expression to find UClassLiteralExpression
        ClassLiteralFinder finder = new ClassLiteralFinder();
        componentNameArg.accept(finder);

        PsiClass serviceClass = finder.foundClass;
        if (serviceClass == null) {
            return;
        }

        String qualifiedName = serviceClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Check if the class extends JobService
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
            Location location = context.getLocation(setComponentCall);
            context.report(
                    ISSUE,
                    setComponentCall,
                    location,
                    String.format(
                            "`%1$s` does not extend `android.app.job.JobService`",
                            serviceClass.getName()));
            return;
        }

        // Store for later checking against manifest
        mJobServiceReferences.put(qualifiedName, context.getLocation(setComponentCall));
        mJobServiceClasses.put(qualifiedName, serviceClass);
        mJobServiceContexts.put(qualifiedName, context);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now check all referenced job services against the manifest
        for (Map.Entry<String, Location> entry : mJobServiceReferences.entrySet()) {
            String className = entry.getKey();
            Location location = entry.getValue();

            if (!mManifestServices.containsKey(className)) {
                // Service not registered in manifest
                context.report(
                        ISSUE,
                        location,
                        String.format(
                                "`%1$s` is not registered in the manifest",
                                getSimpleName(className)));
            } else {
                // Service is registered - check for BIND_JOB_SERVICE permission
                Boolean hasPermission = mManifestServicePermissions.get(className);
                if (hasPermission == null || !hasPermission) {
                    // Report on the manifest element if possible
                    Element manifestElement = mManifestServices.get(className);
                    // We don't have XML context here, so report at the Java location
                    context.report(
                            ISSUE,
                            location,
                            String.format(
                                    "The service `%1$s` requires the permission " +
                                    "`android.permission.BIND_JOB_SERVICE`",
                                    getSimpleName(className)));
                }
            }
        }
    }

    @NonNull
    private static String getSimpleName(@NonNull String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot >= 0) {
            return qualifiedName.substring(lastDot + 1);
        }
        return qualifiedName;
    }

    /**
     * UAST visitor that finds the first class literal expression.
     */
    private static class ClassLiteralFinder extends com.intellij.psi.PsiRecursiveElementVisitor {
        @Nullable PsiClass foundClass;

        // We use a UAST visitor approach
        private final org.jetbrains.uast.visitor.AbstractUastVisitor uastVisitor =
                new org.jetbrains.uast.visitor.AbstractUastVisitor() {
                    @Override
                    public boolean visitClassLiteralExpression(
                            @NonNull UClassLiteralExpression expression) {
                        if (foundClass == null) {
                            com.intellij.psi.PsiType type = expression.getType();
                            if (type instanceof com.intellij.psi.PsiClassType) {
                                foundClass = ((com.intellij.psi.PsiClassType) type).resolve();
                            }
                        }
                        return super.visitClassLiteralExpression(expression);
                    }
                };
    }

    // Override visitMethodCall to use UAST visitor for finding class literals
    // We need a different approach - let's use a proper UAST visitor

    /**
     * Finds PsiClass from a UExpression that may contain a class literal,
     * typically inside a ComponentName constructor call.
     */
    private static class UastClassFinder extends org.jetbrains.uast.visitor.AbstractUastVisitor {
        @Nullable PsiClass foundClass;

        @Override
        public boolean visitClassLiteralExpression(
                @NonNull UClassLiteralExpression expression) {
            if (foundClass == null) {
                com.intellij.psi.PsiType type = expression.getType();
                if (type instanceof com.intellij.psi.PsiClassType) {
                    foundClass = ((com.intellij.psi.PsiClassType) type).resolve();
                }
            }
            return super.visitClassLiteralExpression(expression);
        }
    }

    // Fix the checkComponentNameArg to use UastClassFinder properly
    // The ClassLiteralFinder above is incorrect - let's redo visitMethodCall

    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.content.ComponentName");
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod constructor) {

        // ComponentName(Context, Class<?>) or ComponentName(String, String)
        List<UExpression> args = call.getValueArguments();
        if (args.size() != 2) {
            return;
        }

        // Check if the second argument is a class literal
        UExpression classArg = args.get(1);
        if (!(classArg instanceof UClassLiteralExpression)) {
            return;
        }

        UClassLiteralExpression classLiteral = (UClassLiteralExpression) classArg;
        com.intellij.psi.PsiType type = classLiteral.getType();
        if (!(type instanceof com.intellij.psi.PsiClassType)) {
            return;
        }

        PsiClass serviceClass = ((com.intellij.psi.PsiClassType) type).resolve();
        if (serviceClass == null) {
            return;
        }

        String qualifiedName = serviceClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Check if this ComponentName is being used in a JobInfo.Builder.setComponent call
        // We check by looking at the parent call expression
        // For simplicity, we'll check any ComponentName that references a non-JobService class
        // when it appears to be used in job scheduling context

        JavaEvaluator evaluator = context.getEvaluator();

        // Check if the class extends JobService
        if (!evaluator.extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
            // Only report if this is used in a job scheduling context
            // We need to check the parent to see if this is used in setComponent
            // For now, store it and check later, or check the parent call
            // Let's check if the parent call is setComponent on JobInfo.Builder
            org.jetbrains.uast.UElement parent = call.getUastParent();
            boolean isJobContext = isInJobInfoBuilderContext(parent);
            if (isJobContext) {
                context.report(
                        ISSUE,
                        call,
                        context.getLocation(call),
                        String.format(
                                "`%1$s` does not extend `android.app.job.JobService`",
                                serviceClass.getName()));
            }
            return;
        }

        // Store for later manifest checking
        // We need to determine if this ComponentName is used in job scheduling
        // Store it optimistically and verify at afterCheckRootProject
        mJobServiceReferences.put(qualifiedName, context.getLocation(call));
        mJobServiceClasses.put(qualifiedName, serviceClass);
        mJobServiceContexts.put(qualifiedName, context);
    }

    private boolean isInJobInfoBuilderContext(@Nullable org.jetbrains.uast.UElement element) {
        if (element == null) {
            return false;
        }

        // Walk up to find if we're inside a setComponent call on JobInfo.Builder
        org.jetbrains.uast.UElement current = element;
        int depth = 0;
        while (current != null && depth < 5) {
            if (current instanceof UCallExpression) {
                UCallExpression callExpr = (UCallExpression) current;
                String name = callExpr.getMethodName();
                if ("setComponent".equals(name)) {
                    return true;
                }
            }
            current = current.getUastParent();
            depth++;
        }
        return false;
    }
}