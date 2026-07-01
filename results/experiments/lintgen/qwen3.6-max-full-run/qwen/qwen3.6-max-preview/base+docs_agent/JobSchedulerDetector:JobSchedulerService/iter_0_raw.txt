package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

public class JobSchedulerDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "JobSchedulerService",
        "JobScheduler problems",
        "This check looks for various common mistakes in using the JobScheduler API: " +
        "the service class must extend `JobService`, the service must be registered in the manifest " +
        "and the registration must require the permission `android.permission.BIND_JOB_SERVICE`.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
    );

    private final Map<String, Location> mJobServiceLocations = new HashMap<>();
    private final Map<String, Location> mManifestServiceLocations = new HashMap<>();
    private final Set<String> mManifestServicesWithPermission = new HashSet<>();

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                if (context.getEvaluator().extendsClass(node, "android.app.job.JobService", false)) {
                    String fqn = node.getQualifiedName();
                    if (fqn != null) {
                        mJobServiceLocations.put(fqn, context.getLocation(node));
                    }
                }
            }
        };
    }

    @Override
    public Collection<String> getApplicableXmlFiles() {
        return Collections.singletonList(SdkConstants.ANDROID_MANIFEST_XML);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element manifest = document.getDocumentElement();
        if (manifest == null) return;

        String pkg = manifest.getAttribute("package");
        if (pkg == null || pkg.isEmpty()) return;

        NodeList applications = manifest.getElementsByTagName("application");
        for (int i = 0; i < applications.getLength(); i++) {
            Element app = (Element) applications.item(i);
            NodeList services = app.getElementsByTagName("service");
            for (int j = 0; j < services.getLength(); j++) {
                Element service = (Element) services.item(j);
                String name = service.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if (name == null || name.isEmpty()) continue;

                String fqn = resolveClassName(pkg, name);
                mManifestServiceLocations.put(fqn, context.getLocation(service));

                String permission = service.getAttributeNS(SdkConstants.ANDROID_URI, "permission");
                if ("android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    mManifestServicesWithPermission.add(fqn);
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mJobServiceLocations.entrySet()) {
            String fqn = entry.getKey();
            Location classLocation = entry.getValue();

            if (!mManifestServiceLocations.containsKey(fqn)) {
                context.report(ISSUE, classLocation,
                    "JobService `" + fqn + "` must be registered in the manifest");
            } else if (!mManifestServicesWithPermission.contains(fqn)) {
                context.report(ISSUE, mManifestServiceLocations.get(fqn),
                    "JobService `" + fqn + "` must require `android.permission.BIND_JOB_SERVICE` permission");
            }
        }

        mJobServiceLocations.clear();
        mManifestServiceLocations.clear();
        mManifestServicesWithPermission.clear();
    }

    @NonNull
    private static String resolveClassName(@NonNull String pkg, @NonNull String name) {
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }
}