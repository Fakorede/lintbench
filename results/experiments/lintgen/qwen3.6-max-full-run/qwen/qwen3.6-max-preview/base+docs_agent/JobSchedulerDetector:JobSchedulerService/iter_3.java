package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JobSchedulerDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "JobSchedulerService",
        "JobScheduler problems",
        "This check looks for various common mistakes in using the JobScheduler API: " +
        "the service class must extend `JobService`, the service must be registered in the manifest " +
        "and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(JobSchedulerDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST))
    );

    private final Map<String, Location> jobServices = new HashMap<>();
    private final Map<String, Location> manifestServices = new HashMap<>();
    private final Set<String> manifestServicesWithPermission = new HashSet<>();

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (context.getEvaluator().extendsClass(node, "android.app.job.JobService", false)) {
                    String fqn = node.getQualifiedName();
                    if (fqn != null) {
                        jobServices.put(fqn, context.getLocation((UElement) node));
                    }
                }
            }
        };
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.file == null || !SdkConstants.ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        Element manifest = element.getOwnerDocument().getDocumentElement();
        if (manifest == null) return;
        String pkg = manifest.getAttribute("package");
        if (pkg == null || pkg.isEmpty()) return;

        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
        if (name == null || name.isEmpty()) return;

        String fqn = resolveClassName(pkg, name);
        manifestServices.put(fqn, context.getLocation(element));

        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, "permission");
        if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
            manifestServicesWithPermission.add(fqn);
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<String, Location> entry : jobServices.entrySet()) {
            String fqn = entry.getKey();
            Location classLocation = entry.getValue();

            if (!manifestServices.containsKey(fqn)) {
                context.report(ISSUE, classLocation,
                    "JobService `" + fqn + "` must be registered in the manifest");
            } else if (!manifestServicesWithPermission.contains(fqn)) {
                context.report(ISSUE, manifestServices.get(fqn),
                    "JobService `" + fqn + "` must require `android.permission.BIND_JOB_SERVICE` permission");
            }
        }
        jobServices.clear();
        manifestServices.clear();
        manifestServicesWithPermission.clear();
    }

    private static String resolveClassName(String pkg, String name) {
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }
}