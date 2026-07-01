package com.android.tools.lint.checks;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH Intent Filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "<intent-filter> for the action "
                            + "<literal>android.media.action.MEDIA_PLAY_FROM_SEARCH</literal>. "
                            + "Add this <intent-filter> to an <activity> or <service> in your "
                            + "manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.MANIFEST_SCOPE))
                    .setAndroidSpecific(true);

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String CLASS_MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String CLASS_MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String CLASS_MEDIA_BROWSER_SERVICE_COMPATX =
            "androidx.media.MediaBrowserServiceCompat";

    private final List<UClass> mMediaBrowserServices = new ArrayList<>();
    private final Map<String, Location> mMediaBrowserServiceLocations = new HashMap<>();
    private boolean mHasMediaPlayFromSearchIntentFilter;

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        List<String> elements = new ArrayList<>(2);
        elements.add(com.android.xml.AndroidManifest.NODE_SERVICE);
        elements.add(com.android.xml.AndroidManifest.NODE_ACTIVITY);
        return elements;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mMediaBrowserServices.clear();
        mMediaBrowserServiceLocations.clear();
        mHasMediaPlayFromSearchIntentFilter = false;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = element.getTagName();
        if (!com.android.xml.AndroidManifest.NODE_SERVICE.equals(tag)
                && !com.android.xml.AndroidManifest.NODE_ACTIVITY.equals(tag)) {
            return;
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            if (!com.android.xml.AndroidManifest.NODE_INTENT_FILTER.equals(child.getNodeName())) {
                continue;
            }

            org.w3c.dom.Element intentFilter = (org.w3c.dom.Element) child;
            org.w3c.dom.NodeList actions = intentFilter.getChildNodes();
            for (int j = 0; j < actions.getLength(); j++) {
                org.w3c.dom.Node actionNode = actions.item(j);
                if (actionNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    continue;
                }
                if (!com.android.xml.AndroidManifest.NODE_ACTION.equals(actionNode.getNodeName())) {
                    continue;
                }

                org.w3c.dom.Element action = (org.w3c.dom.Element) actionNode;
                String name =
                        action.getAttributeNS(
                                com.android.SdkConstants.ANDROID_URI,
                                com.android.SdkConstants.ATTR_NAME);
                if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                    mHasMediaPlayFromSearchIntentFilter = true;
                    return;
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        List<String> classes = new ArrayList<>(3);
        classes.add(CLASS_MEDIA_BROWSER_SERVICE);
        classes.add(CLASS_MEDIA_BROWSER_SERVICE_COMPAT);
        classes.add(CLASS_MEDIA_BROWSER_SERVICE_COMPATX);
        return classes;
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String name = declaration.getQualifiedName();
        if (name != null) {
            mMediaBrowserServiceLocations.put(name, context.getLocation(declaration));
        }
        mMediaBrowserServices.add(declaration);
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        // Method callbacks are not required for this manifest check.
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (mHasMediaPlayFromSearchIntentFilter || mMediaBrowserServices.isEmpty()) {
            return;
        }

        for (UClass service : mMediaBrowserServices) {
            String name = service.getQualifiedName();
            if (name == null) {
                continue;
            }
            Location location = mMediaBrowserServiceLocations.get(name);
            if (location == null) {
                continue;
            }
            context.report(
                    ISSUE,
                    service,
                    location,
                    "Missing intent filter for android.media.action.MEDIA_PLAY_FROM_SEARCH. "
                            + "Add this intent-filter to an <activity> or <service> in your "
                            + "manifest to support voice searches on Android Auto.");
        }
    }
}