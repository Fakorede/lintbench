package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";

    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";

    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_EXPORTED = "android:exported";

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION_FILTER =
            Issue.create(
                            "MissingMediaBrowserServiceIntentFilter",
                            "Missing MediaBrowserService intent-filter",
                            "An Automotive Media App requires an exported service that extends "
                                    + "`android.service.media.MediaBrowserService` with an "
                                    + "`intent-filter` for the action "
                                    + "`android.media.browse.MediaBrowserService` to be able to browse "
                                    + "and play media.\n\n"
                                    + "To do this, add\n"
                                    + "```xml\n"
                                    + "<intent-filter>\n"
                                    + "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n"
                                    + "</intent-filter>\n"
                                    + "```\n"
                                    + "to the service that extends "
                                    + "`android.service.media.MediaBrowserService`",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            new Implementation(
                                    AndroidAutoDetector.class,
                                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#config_manifest")
                    .setAndroidSpecific(true);

    // Set of service class names found in the manifest that have the MediaBrowserService action
    // intent-filter
    private final Set<String> mServiceWithIntentFilter = new HashSet<>();

    // Set of service class names found in the manifest (exported or not)
    private final Set<String> mServicesInManifest = new HashSet<>();

    // Set of classes found in source code that extend MediaBrowserService
    private final Set<UClass> mMediaBrowserServiceClasses = new HashSet<>();

    // Whether we've already reported the issue (to avoid duplicates)
    private boolean mIssueReported = false;

    // The XML context for the manifest (used for reporting location)
    private XmlContext mManifestContext = null;

    // Map from service name to its Element in the manifest
    private final java.util.Map<String, Element> mServiceElements = new java.util.HashMap<>();

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mServiceWithIntentFilter.clear();
        mServicesInManifest.clear();
        mMediaBrowserServiceClasses.clear();
        mServiceElements.clear();
        mIssueReported = false;
        mManifestContext = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        mManifestContext = context;

        String serviceName = element.getAttribute(ATTR_NAME);
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        // Normalize the service name
        serviceName = normalizeClassName(serviceName, context.getProject());

        mServicesInManifest.add(serviceName);
        mServiceElements.put(serviceName, element);

        // Check if this service has the MediaBrowserService intent-filter action
        if (hasMediaBrowserServiceIntentFilter(element)) {
            mServiceWithIntentFilter.add(serviceName);
        }
    }

    private boolean hasMediaBrowserServiceIntentFilter(@NonNull Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                if (hasMediaBrowserAction(intentFilter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMediaBrowserAction(@NonNull Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ACTION.equals(child.getNodeName())) {
                Element action = (Element) child;
                String actionName = action.getAttribute(ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (MEDIA_BROWSER_SERVICE.equals(declaration.getQualifiedName())) {
            return;
        }
        mMediaBrowserServiceClasses.add(declaration);
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UCallExpression node,
            @NonNull PsiMethod method) {
        // Not used in this detector, but required by the spec
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIssueReported) {
            return;
        }

        // For each class that extends MediaBrowserService, check if it's declared in the manifest
        // with the correct intent-filter
        for (UClass uClass : mMediaBrowserServiceClasses) {
            String qualifiedName = uClass.getQualifiedName();
            if (qualifiedName == null) {
                continue;
            }

            // Check if this class (or any simple name variant) is in manifest with intent filter
            boolean hasIntentFilter = mServiceWithIntentFilter.contains(qualifiedName);

            if (!hasIntentFilter) {
                // Also check short name variants
                for (String serviceInFilter : mServiceWithIntentFilter) {
                    if (serviceInFilter.endsWith(qualifiedName)
                            || qualifiedName.endsWith(serviceInFilter)) {
                        hasIntentFilter = true;
                        break;
                    }
                }
            }

            if (!hasIntentFilter) {
                // Report on the manifest service element if available, otherwise on the class
                Element serviceElement = mServiceElements.get(qualifiedName);
                if (serviceElement == null) {
                    // Try to find by suffix
                    for (java.util.Map.Entry<String, Element> entry :
                            mServiceElements.entrySet()) {
                        String key = entry.getKey();
                        if (key.endsWith(qualifiedName) || qualifiedName.endsWith(key)) {
                            serviceElement = entry.getValue();
                            break;
                        }
                    }
                }

                if (mManifestContext != null && serviceElement != null) {
                    mManifestContext.report(
                            MISSING_MEDIA_BROWSER_SERVICE_ACTION_FILTER,
                            serviceElement,
                            mManifestContext.getLocation(serviceElement),
                            "This service does not have an `intent-filter` for action "
                                    + "`android.media.browse.MediaBrowserService`");
                } else if (context instanceof JavaContext) {
                    JavaContext javaContext = (JavaContext) context;
                    javaContext.report(
                            MISSING_MEDIA_BROWSER_SERVICE_ACTION_FILTER,
                            uClass,
                            javaContext.getNameLocation(uClass),
                            "This `MediaBrowserService` does not have an `intent-filter` for "
                                    + "action `android.media.browse.MediaBrowserService`");
                }
                mIssueReported = true;
            }
        }
    }

    private String normalizeClassName(@NonNull String name, @NonNull Project project) {
        if (name.startsWith(".")) {
            String packageName = project.getPackage();
            if (packageName != null) {
                return packageName + name;
            }
        }
        return name;
    }
}