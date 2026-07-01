package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
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
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.TAG_SERVICE;

public class JobSchedulerDetector extends Detector implements XmlScanner, SourceCodeScanner {

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
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
        )
    );

    private final List<ServiceInfo> mRegisteredServices = new ArrayList<>();
    private final List<ClassInfo> mProjectClasses = new ArrayList<>();

    private static class ServiceInfo {
        final String rawName;
        final String pkg;
        final String permission;
        final Location location;

        ServiceInfo(String rawName, String pkg, String permission, Location location) {
            this.rawName = rawName;
            this.pkg = pkg;
            this.permission = permission;
            this.location = location;
        }
    }

    private static class ClassInfo {
        final String fqName;
        final boolean inheritsFromJobService;
        final Location location;

        ClassInfo(String fqName, boolean inheritsFromJobService, Location location) {
            this.fqName = fqName;
            this.inheritsFromJobService = inheritsFromJobService;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mRegisteredServices.clear();
        mProjectClasses.clear();
    }

    // XmlScanner implementation

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String className = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (className != null && !className.isEmpty()) {
            String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
            Location location = context.getLocation(element);
            
            String pkg = null;
            Document doc = element.getOwnerDocument();
            if (doc != null && doc.getDocumentElement() != null) {
                pkg = doc.getDocumentElement().getAttribute("package");
            }
            if (pkg == null || pkg.isEmpty()) {
                pkg = context.getProject().getPackage();
            }
            
            mRegisteredServices.add(new ServiceInfo(className, pkg, permission, location));
        }
    }

    // SourceCodeScanner implementation

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
                String fqName = node.getQualifiedName();
                if (fqName == null) {
                    return;
                }
                boolean inheritsFromJobService = context.getEvaluator().inheritsFrom(node, "android.app.job.JobService", false);
                Location location = context.getNameLocation(node);
                mProjectClasses.add(new ClassInfo(fqName, inheritsFromJobService, location));
            }
        };
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (ClassInfo classInfo : mProjectClasses) {
            ServiceInfo registered = findRegisteredService(classInfo.fqName);
            if (classInfo.inheritsFromJobService) {
                if (registered == null) {
                    context.report(
                        ISSUE,
                        classInfo.location,
                        "This JobService class must be registered in the manifest"
                    );
                } else {
                    if (!"android.permission.BIND_JOB_SERVICE".equals(registered.permission)) {
                        context.report(
                            ISSUE,
                            registered.location,
                            "JobService registered in the manifest must require the `android.permission.BIND_JOB_SERVICE` permission"
                        );
                    }
                }
            } else {
                if (registered != null) {
                    if ("android.permission.BIND_JOB_SERVICE".equals(registered.permission)) {
                        context.report(
                            ISSUE,
                            registered.location,
                            String.format("Service `%1$s` must extend `android.app.job.JobService`", classInfo.fqName)
                        );
                    }
                }
            }
        }
    }

    private ServiceInfo findRegisteredService(String fqName) {
        for (ServiceInfo service : mRegisteredServices) {
            if (classMatches(fqName, service)) {
                return service;
            }
        }
        return null;
    }

    private boolean classMatches(String fqName, ServiceInfo service) {
        String rawName = service.rawName;
        String pkg = service.pkg;
        
        if (fqName.equals(rawName)) {
            return true;
        }
        
        String normFqName = fqName.replace('$', '.');
        String normRawName = rawName.replace('$', '.');
        
        if (normFqName.equals(normRawName)) {
            return true;
        }
        
        if (normRawName.startsWith(".")) {
            if (pkg != null && !pkg.isEmpty()) {
                if (normFqName.equals(pkg + normRawName)) {
                    return true;
                }
            }
            return normFqName.endsWith(normRawName);
        }
        
        if (!normRawName.contains(".")) {
            if (pkg != null && !pkg.isEmpty()) {
                if (normFqName.equals(pkg + "." + normRawName)) {
                    return true;
                }
            }
            return normFqName.endsWith("." + normRawName);
        }
        
        return false;
    }
}