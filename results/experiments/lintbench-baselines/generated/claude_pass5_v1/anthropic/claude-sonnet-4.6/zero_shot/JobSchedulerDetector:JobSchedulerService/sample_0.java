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
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Detector for common mistakes when using the JobScheduler API.
 *
 * <p>Checks that:
 * <ul>
 *   <li>The class passed to {@code JobInfo.Builder} extends {@code JobService}</li>
 *   <li>The service is registered in the manifest</li>
 *   <li>The manifest registration requires {@code android.permission.BIND_JOB_SERVICE}</li>
 * </ul>
 */
public class JobSchedulerDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    private static final String JOB_INFO_BUILDER_CLASS = "android.app.job.JobInfo.Builder";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";
    private static final String CLASS_JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String CONSTRUCTOR_NAME = "<init>";

    /** The main issue reported by this detector. */
    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the " +
            " JobScheduler API: the service class must extend `JobService`, " +
            " the service must be registered in the manifest and the registration " +
            " must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                    Scope.JAVA_FILE_SCOPE,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/topic/performance/scheduling.html");

    /** Constructs a new {@link JobSchedulerDetector}. */
    public JobSchedulerDetector() {
    }

    // ---- Implements UastScanner ----

    @Override
    @Nullable
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList(JOB_INFO_BUILDER_CLASS);
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {

        JavaEvaluator evaluator = context.getEvaluator();

        // JobInfo.Builder(int jobId, ComponentName componentName)
        // We want to check the ComponentName argument to see what class it refers to.
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }

        // The second argument is the ComponentName. We need to resolve the class name
        // from it. ComponentName can be constructed as:
        //   new ComponentName(context, MyJobService.class)
        //   new ComponentName(packageName, className)
        // We look for the class literal form.
        UExpression componentNameArg = arguments.get(1);

        // Try to resolve the ComponentName constructor to find the class
        // We look for ComponentName(Context, Class<?>) or ComponentName(String, String)
        if (componentNameArg instanceof UCallExpression) {
            UCallExpression componentNameCall = (UCallExpression) componentNameArg;
            checkComponentNameCall(context, evaluator, componentNameCall, call);
        }
    }

    private void checkComponentNameCall(
            @NonNull JavaContext context,
            @NonNull JavaEvaluator evaluator,
            @NonNull UCallExpression componentNameCall,
            @NonNull UCallExpression jobInfoBuilderCall) {

        List<UExpression> args = componentNameCall.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        // Check for ComponentName(Context, Class<?>) form
        UExpression secondArg = args.get(1);

        // Try to get the class type from the second argument (e.g. MyService.class)
        org.jetbrains.uast.UClassLiteralExpression classLiteral = null;
        if (secondArg instanceof org.jetbrains.uast.UClassLiteralExpression) {
            classLiteral = (org.jetbrains.uast.UClassLiteralExpression) secondArg;
        }

        if (classLiteral == null) {
            return;
        }

        com.intellij.psi.PsiType type = classLiteral.getType();
        if (type == null) {
            return;
        }

        // Resolve the class
        PsiClass serviceClass = null;
        if (type instanceof com.intellij.psi.PsiClassType) {
            serviceClass = ((com.intellij.psi.PsiClassType) type).resolve();
        }

        if (serviceClass == null) {
            return;
        }

        // Check that the service class extends JobService
        if (!evaluator.extendsClass(serviceClass, JOB_SERVICE_CLASS, false)) {
            String className = serviceClass.getQualifiedName();
            if (className == null) {
                className = serviceClass.getName();
            }
            context.report(
                    ISSUE,
                    jobInfoBuilderCall,
                    context.getLocation(secondArg),
                    String.format(
                            "`%1$s` does not extend `android.app.job.JobService`",
                            className));
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this service element is a JobService and has the required permission
        String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);

        if (name == null || name.isEmpty()) {
            return;
        }

        // Check if this service might be a JobService by looking at the permission
        // If it declares android:permission="android.permission.BIND_JOB_SERVICE", it's fine
        // If it doesn't have this permission but IS a JobService, we should warn
        // We can only determine it's a JobService if we have access to the class hierarchy,
        // which requires combined analysis. In manifest-only mode, we check for services
        // that look like they might be job services (heuristic).

        // In the manifest scanner, we check: if a service does NOT have BIND_JOB_SERVICE
        // permission, we flag it. However, we only want to flag services that are actually
        // JobServices. Since we can't easily resolve the class here without combined analysis,
        // we rely on the pattern: any service that should have BIND_JOB_SERVICE.
        //
        // The proper check: look at all services, and if they extend JobService (resolved
        // through the project's class files), ensure they have BIND_JOB_SERVICE.
        // In manifest-only scanning, we check if permission is missing or wrong.

        // For the manifest check, we report if a <service> element is missing the
        // BIND_JOB_SERVICE permission. We need to correlate with Java analysis to know
        // if the service is actually a JobService. We store info for cross-checking.

        // Since this detector runs in both modes, we do a simple check:
        // If the service has no permission attribute at all, or a different permission,
        // and we can determine it extends JobService (via the evaluator in combined mode),
        // we warn. For manifest-only, we skip since we can't resolve the class.

        // The manifest-only check: we look for services that have intent-filter for
        // android.permission.BIND_JOB_SERVICE or similar patterns.
        // Actually, the standard approach: just check that if a service is a JobService
        // (determined in Java scanning), it must have the permission in manifest.
        // We handle this in the Java scan by checking the manifest.

        // For now, implement the manifest check: if a service element lacks the
        // BIND_JOB_SERVICE permission, note it. We'll correlate in combined mode.
        // In practice, this check is done from the Java side using context.getMainProject().

        // Simple manifest-only heuristic: check services that have an intent-filter
        // for JOB_SERVICE or that we know should have BIND_JOB_SERVICE.
        // This is left for the Java-side check which can access both.
    }

    // ---- Combined analysis via UastScanner with manifest access ----

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(JOB_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull org.jetbrains.uast.UClass declaration) {
        PsiClass psiClass = declaration.getJavaPsi();
        JavaEvaluator evaluator = context.getEvaluator();

        // Check that this JobService subclass is registered in the manifest
        // and has the BIND_JOB_SERVICE permission
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        // Look up the manifest for this service
        com.android.tools.lint.detector.api.Project mainProject = context.getMainProject();
        org.w3c.dom.Document mergedManifest = mainProject.getMergedManifest();
        if (mergedManifest == null) {
            return;
        }

        org.w3c.dom.NodeList services = mergedManifest.getElementsByTagName(TAG_SERVICE);
        boolean foundService = false;
        boolean hasCorrectPermission = false;

        String packageName = mainProject.getPackage();

        for (int i = 0; i < services.getLength(); i++) {
            org.w3c.dom.Node node = services.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element serviceElement = (Element) node;
            String serviceName = serviceElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (serviceName == null || serviceName.isEmpty()) {
                continue;
            }

            // Resolve the service name to a fully qualified name
            String resolvedName = resolveServiceName(serviceName, packageName);

            if (qualifiedName.equals(resolvedName)) {
                foundService = true;
                String permission = serviceElement.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
                if (BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                    hasCorrectPermission = true;
                }
                break;
            }
        }

        if (!foundService) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    String.format(
                            "`%1$s` is not registered in the manifest",
                            qualifiedName));
        } else if (!hasCorrectPermission) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    String.format(
                            "`%1$s` does not require the `%2$s` permission",
                            qualifiedName,
                            BIND_JOB_SERVICE_PERMISSION));
        }
    }

    /**
     * Resolves a service name from the manifest to a fully qualified class name.
     */
    @NonNull
    private static String resolveServiceName(@NonNull String name, @Nullable String packageName) {
        if (name.startsWith(".")) {
            // Relative name: prepend package
            if (packageName != null) {
                return packageName + name;
            }
            return name;
        } else if (!name.contains(".")) {
            // Simple name: prepend package
            if (packageName != null) {
                return packageName + "." + name;
            }
            return name;
        }
        // Already fully qualified
        return name;
    }
}