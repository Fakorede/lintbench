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
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.XmlUtils;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

/**
 * Detector for JobScheduler API misuse.
 *
 * <p>Checks:
 * <ul>
 *   <li>The class passed to JobInfo.Builder must extend JobService.</li>
 *   <li>The service must be declared in the AndroidManifest.xml.</li>
 *   <li>The service declaration must require the BIND_JOB_SERVICE permission.</li>
 * </ul>
 */
public class JobSchedulerDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";
    private static final String CONSTRUCTOR_NAME = "<init>";

    /** The main issue */
    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the " +
                    " JobScheduler API: the service class must extend `JobService`," +
                    " the service must be registered in the manifest and the registration" +
                    " must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.MANIFEST_SCOPE));

    // Map from service class name to location of the JobInfo.Builder call
    private final Map<String, Location> mScheduledServices = new HashMap<>();

    // Map from service class name -> whether it has the right permission in manifest
    private final Map<String, Boolean> mManifestServices = new HashMap<>();

    // Pending checks: service class name -> JavaContext (for deferred reporting)
    private final List<PendingCheck> mPendingChecks = new ArrayList<>();

    private static class PendingCheck {
        final String serviceClassName;
        final Location location;
        final JavaContext context;

        PendingCheck(String serviceClassName, Location location, JavaContext context) {
            this.serviceClassName = serviceClassName;
            this.location = location;
            this.context = context;
        }
    }

    public JobSchedulerDetector() {
    }

    // ---- Implements UastScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(CONSTRUCTOR_NAME);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UCallExpression.class);
    }

    @Override
    @Nullable
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitCallExpression(@NonNull UCallExpression call) {
                checkJobInfoBuilderCall(context, call);
            }
        };
    }

    private void checkJobInfoBuilderCall(@NonNull JavaContext context,
            @NonNull UCallExpression call) {
        JavaEvaluator evaluator = context.getEvaluator();

        PsiMethod method = call.resolve();
        if (method == null) {
            return;
        }

        // Check if this is a JobInfo.Builder constructor
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }

        if (!evaluator.inheritsFrom(containingClass, JOB_INFO_BUILDER, false)) {
            // Also check exact class name match for JobInfo.Builder
            String qualifiedName = containingClass.getQualifiedName();
            if (qualifiedName == null || !qualifiedName.equals(JOB_INFO_BUILDER)) {
                return;
            }
        }

        if (!method.isConstructor()) {
            return;
        }

        // The second argument to JobInfo.Builder(int jobId, ComponentName componentName)
        // is a ComponentName. We need to find what class is being scheduled.
        List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        // Try to resolve the ComponentName to a class name
        org.jetbrains.uast.UExpression componentNameArg = args.get(1);
        String serviceClassName = resolveServiceClassName(context, componentNameArg);
        if (serviceClassName == null) {
            return;
        }

        Location location = context.getLocation(call);

        // Check if the class extends JobService
        PsiClass serviceClass = evaluator.findClass(serviceClassName);
        if (serviceClass != null) {
            if (!evaluator.inheritsFrom(serviceClass, JOB_SERVICE_CLASS, false)) {
                context.report(ISSUE, call, location,
                        "Scheduled job class `" + serviceClassName
                                + "` must extend `android.app.job.JobService`");
                return;
            }
        }

        // Defer manifest check
        mPendingChecks.add(new PendingCheck(serviceClassName, location, context));
        mScheduledServices.put(serviceClassName, location);
    }

    @Nullable
    private String resolveServiceClassName(@NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UExpression componentNameArg) {
        // Try to evaluate the expression - look for ComponentName constructor calls
        if (componentNameArg instanceof UCallExpression) {
            UCallExpression componentNameCall = (UCallExpression) componentNameArg;
            List<org.jetbrains.uast.UExpression> cnArgs = componentNameCall.getValueArguments();

            // ComponentName(Context, Class<?>) or ComponentName(String, String)
            if (cnArgs.size() == 2) {
                org.jetbrains.uast.UExpression secondArg = cnArgs.get(1);

                // ComponentName(Context, SomeClass.class) -> class literal
                if (secondArg instanceof org.jetbrains.uast.UClassLiteralExpression) {
                    org.jetbrains.uast.UClassLiteralExpression classLiteral =
                            (org.jetbrains.uast.UClassLiteralExpression) secondArg;
                    com.intellij.psi.PsiType type = classLiteral.getType();
                    if (type instanceof com.intellij.psi.PsiClassType) {
                        PsiClass psiClass = ((com.intellij.psi.PsiClassType) type).resolve();
                        if (psiClass != null) {
                            return psiClass.getQualifiedName();
                        }
                    }
                }

                // ComponentName(String packageName, String className)
                Object secondVal = secondArg.evaluate();
                if (secondVal instanceof String) {
                    return (String) secondVal;
                }
            }
        }

        // Try to evaluate as a variable reference
        Object evaluated = componentNameArg.evaluate();
        if (evaluated instanceof String) {
            return (String) evaluated;
        }

        return null;
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Normalize the class name (handle relative names)
        String packageName = getPackageName(context);
        if (packageName != null && name.startsWith(".")) {
            name = packageName + name;
        } else if (packageName != null && !name.contains(".")) {
            name = packageName + "." + name;
        }

        String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
        boolean hasCorrectPermission = BIND_JOB_SERVICE_PERMISSION.equals(permission);

        mManifestServices.put(name, hasCorrectPermission);

        if (!hasCorrectPermission && mScheduledServices.containsKey(name)) {
            // Report here in XML context
            Location location = context.getLocation(element);
            context.report(ISSUE, element, location,
                    "The service `" + name + "` requires the permission `"
                            + BIND_JOB_SERVICE_PERMISSION + "`");
        }
    }

    @Nullable
    private String getPackageName(@NonNull XmlContext context) {
        Document document = context.document;
        if (document != null) {
            Element root = document.getDocumentElement();
            if (root != null) {
                String pkg = root.getAttribute("package");
                if (pkg != null && !pkg.isEmpty()) {
                    return pkg;
                }
            }
        }
        return null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // After processing all files, check pending items
        for (PendingCheck pending : mPendingChecks) {
            String serviceClassName = pending.serviceClassName;
            Boolean hasPermission = mManifestServices.get(serviceClassName);

            if (hasPermission == null) {
                // Service not found in manifest
                pending.context.report(ISSUE, pending.location,
                        "Did not find a registered service with the name `"
                                + serviceClassName + "`; "
                                + "the service must be declared in the manifest");
            } else if (!hasPermission) {
                // Service found but missing permission - already reported in XML visitor
                // but report again in Java context for completeness if not already done
                // Actually we only report once, in the XML visitor
            }
        }

        mPendingChecks.clear();
        mScheduledServices.clear();
        mManifestServices.clear();
    }
}