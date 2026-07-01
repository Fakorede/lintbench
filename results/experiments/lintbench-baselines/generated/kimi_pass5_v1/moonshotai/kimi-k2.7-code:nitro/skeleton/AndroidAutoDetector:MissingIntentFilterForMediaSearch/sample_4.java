package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should register an intent-filter for the action android.media.action.MEDIA_PLAY_FROM_SEARCH. Add an `<intent-filter>` containing that `<action>` to the relevant `<activity>` or `<service>` in your manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String CLASS_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";
    private static final String CLASS_MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String CLASS_ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT =
            "androidx.media.MediaBrowserServiceCompat";

    private final Set<String> mMediaBrowserServices = new HashSet<>();
    private final Map<String, ComponentInfo> mManifestMediaComponents = new HashMap<>();

    private static class ComponentInfo {
        final Element element;
        final XmlContext context;
        final boolean hasSearchAction;

        ComponentInfo(Element element, XmlContext context, boolean hasSearchAction) {
            this.element = element;
            this.context = context;
            this.hasSearchAction = hasSearchAction;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("service", "activity");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
        mManifestMediaComponents.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (!"service".equals(tag) && !"activity".equals(tag)) {
            return;
        }

        String componentName = getComponentName(context, element);
        if (componentName == null) {
            return;
        }

        boolean hasMediaBrowserService = false;
        boolean hasMediaPlayFromSearch = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !"intent-filter".equals(child.getNodeName())) {
                continue;
            }

            List<String> actions = getActionNames((Element) child);
            for (String action : actions) {
                if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(action)) {
                    hasMediaPlayFromSearch = true;
                }
                if (isMediaBrowserService(action)) {
                    hasMediaBrowserService = true;
                }
            }
        }

        if (hasMediaBrowserService) {
            mManifestMediaComponents.put(
                    componentName,
                    new ComponentInfo(element, context, hasMediaPlayFromSearch));
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                CLASS_MEDIA_BROWSER_SERVICE,
                CLASS_MEDIA_BROWSER_SERVICE_COMPAT,
                CLASS_ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaBrowserServices.add(qualifiedName);
        }
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, ComponentInfo> entry : mManifestMediaComponents.entrySet()) {
            ComponentInfo info = entry.getValue();
            if (!info.hasSearchAction) {
                String message =
                        "Add an `<intent-filter>` with an `<action android:name=\""
                                + ACTION_MEDIA_PLAY_FROM_SEARCH
                                + "\" />` to this component to support voice searches on Android Auto.";
                info.context.report(
                        ISSUE, info.element, info.context.getLocation(info.element), message);
            }
        }
    }

    private String getComponentName(@NonNull XmlContext context, @NonNull Element element) {
        String name = getAttributeValue(element, "name");
        if (name.isEmpty()) {
            return null;
        }

        String packageName = context.getMainProject().getPackage();
        if (packageName == null || packageName.isEmpty()) {
            return name;
        }

        if (name.startsWith(".")) {
            return packageName + name;
        }

        if (!name.contains(".")) {
            return packageName + "." + name;
        }

        return name;
    }

    private static List<String> getActionNames(@NonNull Element intentFilter) {
        List<String> actions = new ArrayList<>();
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "action".equals(node.getNodeName())) {
                actions.add(getAttributeValue((Element) node, "name"));
            }
        }
        return actions;
    }

    private static boolean isMediaBrowserService(@NonNull String action) {
        return CLASS_MEDIA_BROWSER_SERVICE.equals(action)
                || CLASS_MEDIA_BROWSER_SERVICE_COMPAT.equals(action)
                || CLASS_ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT.equals(action);
    }

    private static String getAttributeValue(@NonNull Element element, @NonNull String localName) {
        String value = element.getAttributeNS(ANDROID_URI, localName);
        if (value.isEmpty()) {
            value = element.getAttribute(localName);
        }
        return value;
    }
}