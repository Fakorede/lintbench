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
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

import org.jetbrains.uast.UCallExpression;
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
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
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
            Severity.WARNING,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.MANIFEST_SCOPE
            ))
            .addMoreInfo("https://developer.android.com/topic/performance/scheduling.html");

    /**
     * Map from service class fully-qualified name to the location of the schedule() call
     * that references it.
     */
    private final Map<String, Location> mScheduledServices = new HashMap<>();

    /**
     * Map from service class fully-qualified name to whether it has BIND_JOB_SERVICE permission
     * in the manifest.
     */
    private final Map<String, Boolean> mManifestServices = new HashMap<>();

    public JobSchedulerDetector() {
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("schedule");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull com.intellij.psi.PsiMethod method) {

        JavaEvaluator evaluator = context.getEvaluator();

        // Check that this is JobScheduler.schedule()
        if (!evaluator.isMemberInClass(method, JOB_SCHEDULER_CLASS)) {
            return;
        }

        // The first argument to schedule() is a JobInfo.
        // We need to find the JobInfo.Builder constructor call to get the service class.
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        UExpression jobInfoArg = arguments.get(0);

        // Try to resolve the JobInfo.Builder to find the component class name
        // Walk the expression to find the JobInfo.Builder constructor
        String serviceClassName = findServiceClassName(context, jobInfoArg);

        if (serviceClassName == null) {
            return;
        }

        // Check if the service class extends JobService
        PsiClass serviceClass = evaluator.findClass(serviceClassName);
        if (serviceClass != null) {
            if (!evaluator.extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
                Location location = context.getLocation(call);
                context.report(
                        ISSUE,
                        call,
                        location,
                        String.format(
                                "`%1$s` does not extend `android.app.job.JobService`",
                                serviceClassName));
                return;
            }
        }

        // Store the location for cross-referencing with manifest check
        Location location = context.getLocation(call);
        mScheduledServices.put(serviceClassName, location);
    }

    /**
     * Attempts to find the service class name from a JobInfo expression by looking for
     * a JobInfo.Builder constructor call.
     */
    @Nullable
    private String findServiceClassName(
            @NonNull JavaContext context,
            @NonNull UExpression jobInfoExpression) {

        // Use a visitor to find JobInfo.Builder constructor calls within this expression
        ServiceClassNameVisitor visitor = new ServiceClassNameVisitor(context);
        jobInfoExpression.accept(visitor);
        return visitor.getServiceClassName();
    }

    /**
     * UAST visitor that looks for JobInfo.Builder constructor calls to extract the
     * service component class name.
     */
    private static class ServiceClassNameVisitor extends org.jetbrains.uast.visitor.AbstractUastVisitor {
        private final JavaContext mContext;
        private String mServiceClassName;

        ServiceClassNameVisitor(@NonNull JavaContext context) {
            mContext = context;
        }

        @Nullable
        String getServiceClassName() {
            return mServiceClassName;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            JavaEvaluator evaluator = mContext.getEvaluator();
            com.intellij.psi.PsiMethod resolvedMethod = node.resolve();
            if (resolvedMethod != null) {
                PsiClass containingClass = resolvedMethod.getContainingClass();
                if (containingClass != null) {
                    String qualifiedName = containingClass.getQualifiedName();
                    if (JOB_INFO_BUILDER_CLASS.equals(qualifiedName)
                            && resolvedMethod.isConstructor()) {
                        // JobInfo.Builder(int jobId, ComponentName componentName)
                        List<UExpression> args = node.getValueArguments();
                        if (args.size() >= 2) {
                            UExpression componentNameArg = args.get(1);
                            mServiceClassName = extractClassName(componentNameArg);
                        }
                    }
                }
            }
            return super.visitCallExpression(node);
        }

        @Nullable
        private String extractClassName(@NonNull UExpression componentNameExpr) {
            // Look for ComponentName constructor calls: new ComponentName(context, MyService.class)
            // or new ComponentName(context, "com.example.MyService")
            if (componentNameExpr instanceof UCallExpression) {
                UCallExpression callExpr = (UCallExpression) componentNameExpr;
                List<UExpression> args = callExpr.getValueArguments();
                if (args.size() >= 2) {
                    UExpression classArg = args.get(1);
                    return resolveClassArgument(classArg);
                }
            }
            return null;
        }

        @Nullable
        private String resolveClassArgument(@NonNull UExpression classArg) {
            // Handle MyService.class
            if (classArg instanceof org.jetbrains.uast.UClassLiteralExpression) {
                org.jetbrains.uast.UClassLiteralExpression classLiteral =
                        (org.jetbrains.uast.UClassLiteralExpression) classArg;
                com.intellij.psi.PsiType type = classLiteral.getType();
                if (type != null) {
                    return type.getCanonicalText();
                }
            }
            // Handle "com.example.MyService" string literal
            Object value = classArg.evaluate();
            if (value instanceof String) {
                return (String) value;
            }
            return null;
        }
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

        // Normalize the class name (handle relative names)
        String packageName = context.getMainProject().getPackage();
        if (name.startsWith(".") && packageName != null) {
            name = packageName + name;
        } else if (!name.contains(".") && packageName != null) {
            name = packageName + "." + name;
        }

        String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
        boolean hasBindJobServicePermission = BIND_JOB_SERVICE_PERMISSION.equals(permission);
        mManifestServices.put(name, hasBindJobServicePermission);
    }

    // ---- After all files are processed ----

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Cross-reference scheduled services with manifest entries
        for (Map.Entry<String, Location> entry : mScheduledServices.entrySet()) {
            String serviceClass = entry.getKey();
            Location location = entry.getValue();

            if (!mManifestServices.containsKey(serviceClass)) {
                // Service not registered in manifest
                context.report(
                        ISSUE,
                        location,
                        String.format(
                                "`%1$s` is not registered in the manifest",
                                serviceClass));
            } else {
                Boolean hasPermission = mManifestServices.get(serviceClass);
                if (hasPermission == null || !hasPermission) {
                    // Service registered but missing BIND_JOB_SERVICE permission
                    context.report(
                            ISSUE,
                            location,
                            String.format(
                                    "The manifest registration for `%1$s` does not require the permission "
                                            + "`android.permission.BIND_JOB_SERVICE`",
                                    serviceClass));
                }
            }
        }
    }
}