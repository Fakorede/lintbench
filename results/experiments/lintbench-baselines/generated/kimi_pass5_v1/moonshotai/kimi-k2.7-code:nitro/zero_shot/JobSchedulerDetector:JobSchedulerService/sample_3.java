package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastCallKind;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String JOB_SERVICE = "android.app.job.JobService";
    private static final String JOB_INFO_BUILDER = "android.app.job.JobInfo.Builder";
    private static final String COMPONENT_NAME = "android.content.ComponentName";
    private static final String BIND_JOB_SERVICE = "android.permission.BIND_JOB_SERVICE";

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: the service class must extend `JobService`, the service must be registered in the manifest and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE))
    );

    private final Map<String, Location> mJobServiceClasses = new HashMap<>();
    private final Map<String, ServiceInfo> mManifestServices = new HashMap<>();

    private static class ServiceInfo {
        final Location location;
        final boolean hasJobPermission;

        ServiceInfo(@NonNull Location location, boolean hasJobPermission) {
            this.location = location;
            this.hasJobPermission = hasJobPermission;
        }
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mJobServiceClasses.clear();
        mManifestServices.clear();
    }

    // ---- SourceCodeScanner ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, UCallExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (context.getEvaluator().extendsClass(node, JOB_SERVICE, false)) {
                    String qualifiedName = node.getQualifiedName();
                    if (qualifiedName != null) {
                        mJobServiceClasses.put(qualifiedName, context.getLocation(node));
                    }
                }
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                if (isJobInfoBuilderConstructor(node)) {
                    List<UExpression> args = node.getValueArguments();
                    if (args.size() >= 2) {
                        PsiClass serviceClass = resolveServiceClass(context, args.get(1));
                        if (serviceClass != null
                                && !context.getEvaluator().extendsClass(serviceClass, JOB_SERVICE, false)) {
                            String qualifiedName = serviceClass.getQualifiedName();
                            String name = qualifiedName != null ? qualifiedName : serviceClass.getName();
                            context.report(ISSUE, context.getLocation(node),
                                    "Scheduled service " + name
                                            + " must extend android.app.job.JobService");
                        }
                    }
                }
            }
        };
    }

    // ---- XmlScanner ----

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

        String packageName = context.getProject().getPackage();
        String fqcn = getFullyQualifiedClassName(name, packageName);

        String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
        boolean hasPermission = BIND_JOB_SERVICE.equals(permission);

        mManifestServices.put(fqcn, new ServiceInfo(context.getLocation(element), hasPermission));
    }

    // ---- Cross-file analysis ----

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mJobServiceClasses.entrySet()) {
            String className = entry.getKey();
            Location location = entry.getValue();
            ServiceInfo info = mManifestServices.get(className);
            if (info == null) {
                context.report(ISSUE, location,
                        "This JobService must be registered in the manifest with the android.permission.BIND_JOB_SERVICE permission");
            } else if (!info.hasJobPermission) {
                context.report(ISSUE, location,
                        "This JobService is registered in the manifest but does not require the android.permission.BIND_JOB_SERVICE permission");
            }
        }

        for (Map.Entry<String, ServiceInfo> entry : mManifestServices.entrySet()) {
            String className = entry.getKey();
            ServiceInfo info = entry.getValue();
            if (info.hasJobPermission && !mJobServiceClasses.containsKey(className)) {
                context.report(ISSUE, info.location,
                        "Service " + className + " must extend android.app.job.JobService");
            }
        }
    }

    // ---- Helpers ----

    private static boolean isJobInfoBuilderConstructor(@NonNull UCallExpression node) {
        if (node.getKind() != UastCallKind.CONSTRUCTOR_CALL) {
            return false;
        }
        PsiMethod method = node.resolve();
        if (method == null || !method.isConstructor()) {
            return false;
        }
        PsiClass containingClass = method.getContainingClass();
        return containingClass != null
                && JOB_INFO_BUILDER.equals(containingClass.getQualifiedName());
    }

    @Nullable
    private static PsiClass resolveServiceClass(
            @NonNull JavaContext context,
            @NonNull UExpression expression) {
        if (expression instanceof UClassLiteralExpression) {
            return getClassFromLiteral((UClassLiteralExpression) expression);
        }

        if (expression instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) expression).getValue();
            if (value instanceof String) {
                String packageName = context.getProject().getPackage();
                String fqcn = getFullyQualifiedClassName((String) value, packageName);
                return context.getEvaluator().findClass(fqcn);
            }
            return null;
        }

        if (expression instanceof UCallExpression) {
            UCallExpression call = (UCallExpression) expression;
            PsiMethod method = call.resolve();
            if (method != null && method.isConstructor()) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null
                        && COMPONENT_NAME.equals(containingClass.getQualifiedName())) {
                    List<UExpression> args = call.getValueArguments();
                    if (args.size() == 2) {
                        return resolveServiceClass(context, args.get(1));
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static PsiClass getClassFromLiteral(@NonNull UClassLiteralExpression expression) {
        PsiType type = expression.getType();
        if (!(type instanceof PsiClassType)) {
            return null;
        }
        PsiClassType classType = (PsiClassType) type;
        PsiClass psiClass = classType.resolve();
        if (psiClass != null) {
            String name = psiClass.getQualifiedName();
            if (name != null && !"java.lang.Class".equals(name)) {
                return psiClass;
            }
        }
        PsiType[] parameters = classType.getParameters();
        if (parameters.length == 1 && parameters[0] instanceof PsiClassType) {
            return ((PsiClassType) parameters[0]).resolve();
        }
        return null;
    }

    @NonNull
    private static String getFullyQualifiedClassName(
            @NonNull String className,
            @Nullable String packageName) {
        if (className.startsWith(".")) {
            return packageName != null ? packageName + className : className;
        }
        if (className.indexOf('.') >= 0) {
            return className;
        }
        return packageName != null ? packageName + "." + className : className;
    }
}