package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "JobSchedulerService",
            "JobScheduler problems",
            "This check looks for various common mistakes in using the JobScheduler API: " +
            "the service class must extend `JobService`, the service must be registered in " +
            "the manifest and the registration must require the permission " +
            "`android.permission.BIND_JOB_SERVICE`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    JobSchedulerDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
            )
    );

    private final Map<String, ClassInfo> mJobServices = new HashMap<>();
    private final Map<String, ServiceInfo> mRegisteredServices = new HashMap<>();

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mJobServices.clear();
        mRegisteredServices.clear();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (context.getEvaluator().isAbstract(node)) {
                    return;
                }
                if (context.getEvaluator().extendsClass(node, "android.app.job.JobService", false)) {
                    String qualifiedName = node.getQualifiedName();
                    if (qualifiedName != null) {
                        String shortName = node.getName();
                        if (shortName == null) {
                            shortName = qualifiedName;
                        }
                        mJobServices.put(qualifiedName, new ClassInfo(qualifiedName, shortName, context.getNameLocation(node)));
                    }
                }
            }
        };
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }
        Element root = element.getOwnerDocument().getDocumentElement();
        String packageName = root.getAttribute(SdkConstants.ATTR_PACKAGE);
        if (packageName == null || packageName.isEmpty()) {
            packageName = context.getProject().getPackage();
        }
        String fqcn = resolveClassName(name, packageName);
        if (fqcn == null) {
            return;
        }

        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
        if (permission != null && permission.isEmpty()) {
            permission = null;
        }
        Attr permissionAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_PERMISSION);
        Location location = context.getLocation(element);
        Location permissionLocation = permissionAttr != null ? context.getLocation(permissionAttr) : null;

        ServiceInfo info = new ServiceInfo(permission, location, permissionLocation);
        mRegisteredServices.put(fqcn, info);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        JavaEvaluator evaluator = context.getClient().getUastParser(context.getProject()).getEvaluator();

        for (ClassInfo classInfo : mJobServices.values()) {
            ServiceInfo serviceInfo = mRegisteredServices.get(classInfo.qualifiedName);
            if (serviceInfo == null) {
                context.report(
                        ISSUE,
                        classInfo.location,
                        "Scheduled job service " + classInfo.shortName + " must be registered in the manifest"
                );
            } else {
                if (!"android.permission.BIND_JOB_SERVICE".equals(serviceInfo.permission)) {
                    Location reportLocation = serviceInfo.permissionLocation != null ? serviceInfo.permissionLocation : serviceInfo.location;
                    context.report(
                            ISSUE,
                            reportLocation,
                            "JobService " + classInfo.shortName + " must require android.permission.BIND_JOB_SERVICE"
                    );
                }
            }
        }

        for (Map.Entry<String, ServiceInfo> entry : mRegisteredServices.entrySet()) {
            String fqcn = entry.getKey();
            ServiceInfo serviceInfo = entry.getValue();
            if ("android.permission.BIND_JOB_SERVICE".equals(serviceInfo.permission)) {
                if (!mJobServices.containsKey(fqcn)) {
                    PsiClass psiClass = evaluator.findClass(fqcn);
                    if (psiClass != null) {
                        if (!evaluator.extendsClass(psiClass, "android.app.job.JobService", false)) {
                            Location reportLocation = serviceInfo.location;
                            context.report(
                                    ISSUE,
                                    reportLocation,
                                    "The service class must extend android.app.job.JobService"
                            );
                        }
                    }
                }
            }
        }
    }

    @Nullable
    private String resolveClassName(@NonNull String className, @Nullable String packageName) {
        if (className.isEmpty()) {
            return null;
        }
        if (className.startsWith(".")) {
            return packageName != null ? packageName + className : className;
        }
        if (!className.contains(".")) {
            return packageName != null ? packageName + "." + className : className;
        }
        return className;
    }

    private static class ClassInfo {
        @NonNull final String qualifiedName;
        @NonNull final String shortName;
        @NonNull final Location location;

        ClassInfo(@NonNull String qualifiedName, @NonNull String shortName, @NonNull Location location) {
            this.qualifiedName = qualifiedName;
            this.shortName = shortName;
            this.location = location;
        }
    }

    private static class ServiceInfo {
        @Nullable final String permission;
        @NonNull final Location location;
        @Nullable final Location permissionLocation;

        ServiceInfo(@Nullable String permission, @NonNull Location location, @Nullable final Location permissionLocation) {
            this.permission = permission;
            this.location = location;
            this.permissionLocation = permissionLocation;
        }
    }
}