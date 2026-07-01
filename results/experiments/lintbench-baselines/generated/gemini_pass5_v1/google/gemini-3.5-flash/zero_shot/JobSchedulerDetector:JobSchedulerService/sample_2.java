package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.TAG_SERVICE;

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
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private final Map<String, ServiceDeclaration> manifestServices = new HashMap<>();
    private final Map<String, ClassDeclaration> jobServiceClasses = new HashMap<>();

    private static class ServiceDeclaration {
        String name;
        String permission;
        Location location;
    }

    private static class ClassDeclaration {
        String fqName;
        Location location;
        JavaContext context;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        manifestServices.clear();
        jobServiceClasses.clear();
    }

    // XmlScanner implementation

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttr == null) return;
        String name = nameAttr.getValue();
        String fqName = resolveClassName(context, name);

        Attr permAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_PERMISSION);
        String permission = permAttr != null ? permAttr.getValue() : null;

        ServiceDeclaration decl = new ServiceDeclaration();
        decl.name = fqName;
        decl.permission = permission;
        decl.location = context.getLocation(element);
        manifestServices.put(fqName, decl);
    }

    private String resolveClassName(XmlContext context, String name) {
        if (name == null) return null;
        if (name.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            return pkg != null ? pkg + name : name;
        } else if (!name.contains(".")) {
            String pkg = context.getProject().getPackage();
            return pkg != null ? pkg + "." + name : name;
        }
        return name;
    }

    // SourceCodeScanner implementation

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.job.JobService");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }
        String fqName = declaration.getQualifiedName();
        if (fqName == null) return;

        ClassDeclaration decl = new ClassDeclaration();
        decl.fqName = fqName;
        decl.location = context.getNameLocation(declaration);
        decl.context = context;
        jobServiceClasses.put(fqName, decl);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (ClassDeclaration jobClass : jobServiceClasses.values()) {
            ServiceDeclaration serviceDecl = manifestServices.get(jobClass.fqName);
            if (serviceDecl == null) {
                jobClass.context.report(
                    ISSUE,
                    jobClass.location,
                    "The service `" + jobClass.fqName + "` must be registered in the manifest"
                );
            } else {
                if (!"android.permission.BIND_JOB_SERVICE".equals(serviceDecl.permission)) {
                    context.report(
                        ISSUE,
                        serviceDecl.location,
                        "JobService `" + jobClass.fqName + "` must require the permission `android.permission.BIND_JOB_SERVICE`"
                    );
                }
            }
        }
    }
}