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
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService Intent Filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `<intent-filter>` "
                            + "for the action `android.media.browse.MediaBrowserService`. "
                            + "Add the intent-filter to the service that extends MediaBrowserService.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class, Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE));

    private final Map<String, ServiceInfo> mServices = new HashMap<>();
    private final Set<String> mMediaBrowserServiceClasses = new HashSet<>();
    private final Set<String> mPendingReports = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        return name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".kt");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mServices.clear();
        mMediaBrowserServiceClasses.clear();
        mPendingReports.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        String name = getAttribute(element, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.getMainProject().getPackage();
        String fqn = resolveClassName(name, packageName);

        ServiceInfo info = new ServiceInfo(element, context);
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element filter = (Element) child;
                Node actionNode = filter.getFirstChild();
                while (actionNode != null) {
                    if (actionNode.getNodeType() == Node.ELEMENT_NODE
                            && TAG_ACTION.equals(actionNode.getNodeName())) {
                        String action = getAttribute((Element) actionNode, ATTR_NAME);
                        if (MEDIA_BROWSER_SERVICE_ACTION.equals(action)) {
                            info.hasMediaBrowseFilter = true;
                        }
                    }
                    actionNode = actionNode.getNextSibling();
                }
            }
            child = child.getNextSibling();
        }

        mServices.put(fqn, info);

        if (mMediaBrowserServiceClasses.contains(fqn) && !info.hasMediaBrowseFilter) {
            reportMissingFilter(info);
        }

        if (mPendingReports.contains(fqn) && !info.hasMediaBrowseFilter) {
            reportMissingFilter(info);
            mPendingReports.remove(fqn);
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqn = declaration.getQualifiedName();
        if (fqn == null) {
            return;
        }

        mMediaBrowserServiceClasses.add(fqn);

        ServiceInfo info = mServices.get(fqn);
        if (info != null && !info.hasMediaBrowseFilter) {
            reportMissingFilter(info);
        } else if (info == null) {
            mPendingReports.add(fqn);
        }
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context, @NonNull UMethod node, @NonNull PsiMethod method) {
        // No method-level checks are required for this issue.
    }

    private void reportMissingFilter(@NonNull ServiceInfo info) {
        if (info.reported) {
            return;
        }
        info.reported = true;
        info.context.report(
                ISSUE,
                info.element,
                info.location,
                "The service extending MediaBrowserService must declare an `<intent-filter>` "
                        + "with action `android.media.browse.MediaBrowserService`.");
    }

    @Nullable
    private static String getAttribute(@NonNull Element element, @NonNull String localName) {
        if (element.hasAttributeNS(ANDROID_URI, localName)) {
            return element.getAttributeNS(ANDROID_URI, localName);
        }
        String value = element.getAttribute(localName);
        return value.isEmpty() ? null : value;
    }

    @NonNull
    private static String resolveClassName(@NonNull String name, @Nullable String packageName) {
        if (name.startsWith(".")) {
            return (packageName != null ? packageName : "") + name;
        }
        if (packageName != null && !name.contains(".")) {
            return packageName + "." + name;
        }
        return name;
    }

    private static class ServiceInfo {
        final Element element;
        final XmlContext context;
        final Location location;
        boolean hasMediaBrowseFilter;
        boolean reported;

        ServiceInfo(@NonNull Element element, @NonNull XmlContext context) {
            this.element = element;
            this.context = context;
            this.location = context.getLocation(element);
        }
    }
}