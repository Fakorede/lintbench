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
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE_COMPATX =
            "androidx.media.MediaBrowserServiceCompat";

    private static final String ANDROID_MANIFEST_FILE = "AndroidManifest.xml";
    private static final String ANDROID_PREFIX = "android:";
    private static final String ATTR_NAME = "name";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, a media browser service "
                            + "component must also declare an <intent-filter> for the action "
                            + "\"android.media.action.MEDIA_PLAY_FROM_SEARCH\". Add the filter to "
                            + "the corresponding <activity> or <service> in your manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Set<String> mMediaBrowserServices = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SERVICE, TAG_ACTIVITY);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!ANDROID_MANIFEST_FILE.equals(context.file.getName())) {
            return;
        }

        String name = element.getAttribute(ANDROID_PREFIX + ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String fqcn = normalizeClassName(getFullClassName(context, name));
        if (!mMediaBrowserServices.contains(fqcn) && !hasMediaBrowserServiceFilter(element)) {
            return;
        }

        if (!hasAction(element, MEDIA_PLAY_FROM_SEARCH)) {
            context.report(
                    ISSUE,
                    element,
                    context.getElementLocation(element),
                    "Add an <intent-filter> with "
                            + "<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" /> "
                            + "to this component to support voice searches on Android Auto.");
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_BROWSER_SERVICE,
                MEDIA_BROWSER_SERVICE_COMPAT,
                MEDIA_BROWSER_SERVICE_COMPATX);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaBrowserServices.add(normalizeClassName(qualifiedName));
        }
    }

    private static boolean hasMediaBrowserServiceFilter(@NonNull Element component) {
        return hasAction(component, MEDIA_BROWSER_SERVICE);
    }

    private static boolean hasAction(@NonNull Element component, @NonNull String actionName) {
        NodeList children = component.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (!(child instanceof Element) || !TAG_INTENT_FILTER.equals(child.getNodeName())) {
                continue;
            }
            NodeList actions = child.getChildNodes();
            for (int j = 0, m = actions.getLength(); j < m; j++) {
                Node actionNode = actions.item(j);
                if (actionNode instanceof Element && TAG_ACTION.equals(actionNode.getNodeName())) {
                    String value = ((Element) actionNode).getAttribute(ANDROID_PREFIX + ATTR_NAME);
                    if (actionName.equals(value)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @NonNull
    private static String getFullClassName(@NonNull XmlContext context, @NonNull String name) {
        String packageName = context.getMainProject().getPackage();
        if (packageName == null) {
            packageName = "";
        }
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.contains(".")) {
            return name;
        }
        return packageName.isEmpty() ? name : packageName + "." + name;
    }

    @NonNull
    private static String normalizeClassName(@NonNull String name) {
        return name.replace('$', '.');
    }
}