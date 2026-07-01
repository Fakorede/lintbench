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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Detector for common mistakes when using the JobScheduler API.
 */
public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, the service must be registered " +
            "in the manifest and the registration must require the permission " +
            "`android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                    Scope.MANIFEST_SCOPE,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/topic/performance/scheduling.html");

    private static final String JOB_SCHEDULER_CLASS = "android.app.job.JobScheduler";
    private static final String JOB_INFO_CLASS = "android.app.job.JobInfo";
    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static final String SCHEDULE_METHOD = "schedule";
    private static final String BUILDER_CONSTRUCTOR = "Builder";

    // Map from service class name to the location of the schedule() call
    // Key: fully qualified class name of the component passed to JobInfo.Builder
    // Value: location of the schedule call
    private final Map<String, Location> mScheduledServices = new HashMap<>();

    // Map from service class name to whether it extends JobService
    private final Map<String, Boolean> mServiceExtendsJobService = new HashMap<>();

    // Services registered in manifest: class name -> permission
    private final Map<String, String> mManifestServices = new HashMap<>();

    // Pending issues to report after we have all information
    // We store: service class name -> call location
    private final Map<String, Location> mPendingChecks = new HashMap<>();

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // After all files have been analyzed, report issues
        for (Map.Entry<String, Location> entry : mPendingChecks.entrySet()) {
            String serviceClassName = entry.getKey();
            Location location = entry.getValue();

            // Check if the service class extends JobService
            Boolean extendsJobService = mServiceExtendsJobService.get(serviceClassName);
            if (extendsJobService != null && !extendsJobService) {
                context.report(
                        ISSUE,
                        location,
                        "Scheduled job class `" + serviceClassName + "` must extend `android.app.job.JobService`"
                );
                continue;
            }

            // Check if the service is registered in the manifest
            if (!mManifestServices.containsKey(serviceClassName)) {
                // Try simple name match
                String simpleName = getSimpleName(serviceClassName);
                boolean found = false;
                for (String manifestService : mManifestServices.keySet()) {
                    if (manifestService.equals(serviceClassName) ||
                            manifestService.endsWith("." + simpleName) ||
                            manifestService.equals(simpleName)) {
                        found = true;
                        serviceClassName = manifestService;
                        break;
                    }
                }
                if (!found) {
                    context.report(
                            ISSUE,
                            location,
                            "Did not find a registered `<service>` with the name `" + serviceClassName + "`"
                    );
                    continue;
                }
            }

            // Check if the manifest registration requires BIND_JOB_SERVICE permission
            String permission = mManifestServices.get(serviceClassName);
            if (permission == null || !BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                context.report(
                        ISSUE,
                        location,
                        "The service `" + serviceClassName + "` requires the permission " +
                        "`" + BIND_JOB_SERVICE_PERMISSION + "`, add `android:permission=\"" +
                        BIND_JOB_SERVICE_PERMISSION + "\"` to the `<service>` tag"
                );
            }
        }
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Resolve the class name relative to the package
        String packageName = "";
        Document document = element.getOwnerDocument();
        if (document != null) {
            Element root = document.getDocumentElement();
            if (root != null) {
                packageName = root.getAttribute("package");
            }
        }

        String className = resolveClassName(name, packageName);

        String permission = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "permission");

        mManifestServices.put(className, permission);
    }

    // ---- SourceCodeScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(SCHEDULE_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull com.intellij.psi.PsiMethod method) {

        JavaEvaluator evaluator = context.getEvaluator();

        // Check if this is JobScheduler.schedule(JobInfo)
        if (!evaluator.isMemberInClass(method, JOB_SCHEDULER_CLASS)) {
            return;
        }

        // Get the JobInfo argument
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        // Try to find the service class name from the JobInfo.Builder constructor
        // We need to trace back to find where the JobInfo was built
        UExpression jobInfoArg = arguments.get(0);
        String serviceClassName = findServiceClassName(context, jobInfoArg);

        if (serviceClassName == null) {
            return;
        }

        Location location = context.getLocation(call);
        mPendingChecks.put(serviceClassName, location);

        // Check if the class extends JobService
        PsiClass serviceClass = evaluator.findClass(serviceClassName);
        if (serviceClass != null) {
            boolean extendsJobService = evaluator.extendsClass(serviceClass, JOB_SERVICE_CLASS, false);
            mServiceExtendsJobService.put(serviceClassName, extendsJobService);

            if (!extendsJobService) {
                context.report(
                        ISSUE,
                        call,
                        location,
                        "Scheduled job class `" + serviceClassName +
                        "` must extend `android.app.job.JobService`"
                );
                // Remove from pending since we already reported
                mPendingChecks.remove(serviceClassName);
                return;
            }
        }
    }

    @Nullable
    private String findServiceClassName(
            @NonNull JavaContext context,
            @NonNull UExpression jobInfoExpression) {

        // Walk the expression to find a JobInfo.Builder constructor call
        // The typical pattern is:
        //   new JobInfo.Builder(id, new ComponentName(context, MyJobService.class)).build()
        ServiceClassFinder finder = new ServiceClassFinder(context);
        jobInfoExpression.accept(finder);
        return finder.getServiceClassName();
    }

    private static String resolveClassName(String name, String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        } else if (!name.contains(".")) {
            return packageName + "." + name;
        }
        return name;
    }

    private static String getSimpleName(String className) {
        int dot = className.lastIndexOf('.');
        if (dot >= 0) {
            return className.substring(dot + 1);
        }
        return className;
    }

    /**
     * UAST visitor that looks for ComponentName constructor calls within a JobInfo.Builder
     * to extract the service class name.
     */
    private static class ServiceClassFinder extends org.jetbrains.uast.visitor.AbstractUastVisitor {
        private final JavaContext mContext;
        private String mServiceClassName;

        ServiceClassFinder(JavaContext context) {
            mContext = context;
        }

        @Nullable
        String getServiceClassName() {
            return mServiceClassName;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            if (mServiceClassName != null) {
                return false;
            }

            String methodName = node.getMethodName();
            if (methodName == null) {
                return false;
            }

            // Look for ComponentName constructor: new ComponentName(context, MyService.class)
            // or ComponentName(String packageName, String className)
            com.intellij.psi.PsiMethod resolvedMethod = node.resolve();
            if (resolvedMethod == null) {
                return false;
            }

            JavaEvaluator evaluator = mContext.getEvaluator();
            PsiClass containingClass = resolvedMethod.getContainingClass();
            if (containingClass == null) {
                return false;
            }

            String qualifiedName = containingClass.getQualifiedName();
            if ("android.content.ComponentName".equals(qualifiedName)) {
                // ComponentName constructor - try to extract the class
                List<UExpression> args = node.getValueArguments();
                if (args.size() >= 2) {
                    UExpression classArg = args.get(1);
                    // Try to get the class literal: MyService.class
                    String className = extractClassName(classArg, evaluator);
                    if (className != null) {
                        mServiceClassName = className;
                    }
                }
            }

            return false;
        }

        @Nullable
        private String extractClassName(
                @NonNull UExpression expression,
                @NonNull JavaEvaluator evaluator) {

            // Handle MyService.class (UClassLiteralExpression)
            if (expression instanceof org.jetbrains.uast.UClassLiteralExpression) {
                org.jetbrains.uast.UClassLiteralExpression classLiteral =
                        (org.jetbrains.uast.UClassLiteralExpression) expression;
                com.intellij.psi.PsiType type = classLiteral.getType();
                if (type instanceof com.intellij.psi.PsiClassType) {
                    PsiClass psiClass = ((com.intellij.psi.PsiClassType) type).resolve();
                    if (psiClass != null) {
                        return psiClass.getQualifiedName();
                    }
                }
            }

            // Handle string literal for class name
            Object value = expression.evaluate();
            if (value instanceof String) {
                return (String) value;
            }

            return null;
        }
    }
}