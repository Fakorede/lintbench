package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String ACTION_MEDIA_BROWSE = "android.media.browse.MediaBrowserService";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_EXPORTED = "exported";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class, Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE);

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `<intent-filter>` "
                            + "containing the action `android.media.browse.MediaBrowserService`. "
                            + "Add the required intent-filter to this service.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Set<String> mMediaBrowserServices = new HashSet<>();

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return Lint.isManifestFile(file)
                || file.getName().endsWith(".java")
                || file.getName().endsWith(".kt");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String serviceName = getAttribute(element, ATTR_NAME);
        if (serviceName.isEmpty()) {
            return;
        }

        String fqn = resolveServiceName(context, serviceName);
        if (!mMediaBrowserServices.contains(fqn)) {
            return;
        }

        String exported = getAttribute(element, ATTR_EXPORTED);
        if ("false".equals(exported)) {
            return;
        }

        if (hasMediaBrowseAction(element)) {
            return;
        }

        context.report(
                MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER,
                element,
                context.getLocation(element),
                "Missing intent-filter with action android.media.browse.MediaBrowserService");
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqcn = declaration.getQualifiedName();
        if (fqcn != null && !MEDIA_BROWSER_SERVICE.equals(fqcn)) {
            mMediaBrowserServices.add(fqcn);
        }
    }

    public void visitMethod(
            @NonNull JavaContext context, @NonNull UMethod method, @NonNull PsiMethod psiMethod) {
        // No method-level analysis required.
    }

    private static String getAttribute(@NonNull Element element, @NonNull String localName) {
        String value = element.getAttributeNS(ANDROID_URI, localName);
        if (!value.isEmpty()) {
            return value;
        }
        value = element.getAttribute("android:" + localName);
        if (!value.isEmpty()) {
            return value;
        }
        return element.getAttribute(localName);
    }

    private static String resolveServiceName(@NonNull XmlContext context, @NonNull String name) {
        if (name.startsWith(".")) {
            String pkg = context.getMainProject().getPackage();
            return (pkg != null ? pkg : "") + name;
        }
        if (name.contains(".")) {
            return name;
        }
        String pkg = context.getMainProject().getPackage();
        return (pkg != null ? pkg : "") + "." + name;
    }

    private static boolean hasMediaBrowseAction(@NonNull Element service) {
        NodeList children = service.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!TAG_INTENT_FILTER.equals(child.getNodeName())) {
                continue;
            }
            Element filter = (Element) child;
            NodeList actionNodes = filter.getChildNodes();
            for (int j = 0; j < actionNodes.getLength(); j++) {
                Node actionNode = actionNodes.item(j);
                if (actionNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                if (!TAG_ACTION.equals(actionNode.getNodeName())) {
                    continue;
                }
                String actionName = getAttribute((Element) actionNode, ATTR_NAME);
                if (ACTION_MEDIA_BROWSE.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }
}