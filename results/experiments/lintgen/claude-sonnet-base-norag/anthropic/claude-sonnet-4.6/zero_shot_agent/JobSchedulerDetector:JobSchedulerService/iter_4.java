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
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastCallKind;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Detector for common mistakes when using the JobScheduler API.
 */
public class JobSchedulerDetector extends Detector implements Detector.UastScanner {

    private static final String JOB_SCHEDULER_CLASS = "android.app.job.JobScheduler";
    private static final String JOB_SERVICE_CLASS = "android.app.job.JobService";
    private static final String BIND_JOB_SERVICE_PERMISSION = "android.permission.BIND_JOB_SERVICE";
    private static final String COMPONENT_NAME_CLASS = "android.content.ComponentName";
    private static final String SCHEDULE_METHOD = "schedule";

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
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            ))
            .addMoreInfo("https://developer.android.com/topic/performance/scheduling.html");

    public JobSchedulerDetector() {
    }

    @Override
    @Nullable
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(SCHEDULE_METHOD);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();

        // Check that this is JobScheduler.schedule()
        if (!evaluator.isMemberInClass(method, JOB_SCHEDULER_CLASS)) {
            return;
        }

        // The first argument to schedule() is a JobInfo
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        UExpression jobInfoArg = arguments.get(0);
        // Walk the expression tree to find JobInfo.Builder constructor calls
        ServiceClassFinder finder = new ServiceClassFinder(context);
        jobInfoArg.accept(finder);
    }

    /**
     * Visitor that traverses the expression tree looking for JobInfo.Builder constructor calls,
     * then ComponentName constructor calls within them, to find the service class.
     */
    private static class ServiceClassFinder extends AbstractUastVisitor {
        private final JavaContext mContext;

        ServiceClassFinder(@NonNull JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            if (node.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
                PsiMethod constructor = node.resolve();
                if (constructor != null) {
                    PsiClass containingClass = constructor.getContainingClass();
                    if (containingClass != null) {
                        String qualifiedName = containingClass.getQualifiedName();
                        if ("android.app.job.JobInfo.Builder".equals(qualifiedName)) {
                            checkJobInfoBuilderConstructor(mContext, node);
                        }
                    }
                }
            }

            return false; // continue visiting children
        }
    }

    private static void checkJobInfoBuilderConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression constructorCall) {
        // JobInfo.Builder(int jobId, ComponentName componentName)
        List<UExpression> args = constructorCall.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression componentNameArg = args.get(1);
        ComponentNameFinder finder = new ComponentNameFinder(context);
        componentNameArg.accept(finder);
    }

    /**
     * Visitor that looks for ComponentName constructor calls and checks the service class.
     */
    private static class ComponentNameFinder extends AbstractUastVisitor {
        private final JavaContext mContext;

        ComponentNameFinder(@NonNull JavaContext context) {
            mContext = context;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            if (node.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
                PsiMethod constructor = node.resolve();
                if (constructor != null) {
                    PsiClass containingClass = constructor.getContainingClass();
                    if (containingClass != null
                            && COMPONENT_NAME_CLASS.equals(containingClass.getQualifiedName())) {
                        checkComponentNameConstructor(mContext, node);
                    }
                }
            }
            return false;
        }
    }

    private static void checkComponentNameConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression constructorCall) {
        // ComponentName(Context context, Class<?> cls) or ComponentName(Context context, String className)
        List<UExpression> args = constructorCall.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        UExpression classArg = args.get(1);
        JavaEvaluator evaluator = context.getEvaluator();

        PsiClass serviceClass = null;

        // Try to resolve as a class literal (MyService.class)
        if (classArg instanceof UClassLiteralExpression) {
            UClassLiteralExpression classLiteral = (UClassLiteralExpression) classArg;
            PsiType type = classLiteral.getType();
            if (type instanceof PsiClassType) {
                serviceClass = ((PsiClassType) type).resolve();
            }
        }

        if (serviceClass == null) {
            return;
        }

        // Check if the service class extends JobService
        if (!evaluator.inheritsFrom(serviceClass, JOB_SERVICE_CLASS, false)) {
            String className = serviceClass.getQualifiedName();
            if (className == null) {
                className = serviceClass.getName();
            }
            context.report(
                    ISSUE,
                    constructorCall,
                    context.getLocation(classArg),
                    String.format(
                            "`%1$s` does not extend `android.app.job.JobService`",
                            className));
            return;
        }

        // The class extends JobService - now check manifest registration
        String serviceClassName = serviceClass.getQualifiedName();
        if (serviceClassName == null) {
            serviceClassName = serviceClass.getName();
        }
        if (serviceClassName == null) {
            return;
        }

        checkManifestRegistration(context, constructorCall, serviceClassName);
    }

    private static void checkManifestRegistration(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull String serviceClassName) {

        // Get the merged manifest document
        Document mergedManifest = context.getMainProject().getMergedManifest();
        if (mergedManifest == null) {
            return;
        }

        Element root = mergedManifest.getDocumentElement();
        if (root == null) {
            return;
        }

        // Find the <application> element
        Element application = null;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "application".equals(child.getNodeName())) {
                application = (Element) child;
                break;
            }
        }

        if (application == null) {
            return;
        }

        String pkg = context.getMainProject().getPackage();

        // Look for the service in the manifest
        NodeList serviceNodes = application.getChildNodes();
        for (int i = 0; i < serviceNodes.getLength(); i++) {
            Node child = serviceNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_SERVICE.equals(child.getNodeName())) {
                Element serviceElement = (Element) child;
                String name = serviceElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (name == null || name.isEmpty()) {
                    continue;
                }

                // Resolve the fully qualified name
                String fqcn = resolveFqcn(pkg, name);

                if (serviceClassName.equals(fqcn)) {
                    // Found the service registration - check permission
                    String permission = serviceElement.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
                    if (!BIND_JOB_SERVICE_PERMISSION.equals(permission)) {
                        context.report(
                                ISSUE,
                                call,
                                context.getLocation(call),
                                String.format(
                                        "The service `%1$s` requires the permission " +
                                        "`android.permission.BIND_JOB_SERVICE`",
                                        serviceClassName));
                    }
                    return;
                }
            }
        }

        // Service not found in manifest
        context.report(
                ISSUE,
                call,
                context.getLocation(call),
                String.format(
                        "`%1$s` is not registered in the manifest",
                        serviceClassName));
    }

    @NonNull
    private static String resolveFqcn(@Nullable String pkg, @NonNull String name) {
        if (name.startsWith(".")) {
            if (pkg != null) {
                return pkg + name;
            }
            return name;
        } else if (!name.contains(".")) {
            if (pkg != null) {
                return pkg + "." + name;
            }
            return name;
        }
        return name;
    }
}