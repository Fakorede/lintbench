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
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_SERVICE = "service";
    private static final String ATTR_NAME = "android:name";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE));

    public static final Issue MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH =
            Issue.create(
                            "MissingIntentFilterForMediaSearch",
                            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                            "To support voice searches on Android Auto, you should also register an "
                                    + "`intent-filter` for the action "
                                    + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n"
                                    + "\n"
                                    + "To do this, add\n"
                                    + "```xml\n"
                                    + "`<intent-filter>`\n"
                                    + "    `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />`\n"
                                    + "`</intent-filter>`\n"
                                    + "```\n"
                                    + "to your `<activity>` or `<service>`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#support_voice");

    // Whether we found a MediaBrowserService subclass in the source
    private boolean mMediaBrowserServiceFound = false;
    // Whether the manifest has the MEDIA_PLAY_FROM_SEARCH intent filter
    private boolean mMediaPlayFromSearchFound = false;
    // The UClass node to report on if needed
    private UClass mMediaBrowserServiceClass = null;
    // The XmlContext for manifest reporting
    private XmlContext mXmlContext = null;

    public AndroidAutoDetector() {}

    // ---- XmlScanner ----

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
        mMediaBrowserServiceFound = false;
        mMediaPlayFromSearchFound = false;
        mMediaBrowserServiceClass = null;
        mXmlContext = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We're visiting <service> elements in the manifest.
        // Check if any intent-filter inside has MEDIA_PLAY_FROM_SEARCH action.
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getChildNodes();
                for (int j = 0; j < actions.getLength(); j++) {
                    Node actionNode = actions.item(j);
                    if (actionNode.getNodeType() == Node.ELEMENT_NODE
                            && TAG_ACTION.equals(actionNode.getNodeName())) {
                        Element action = (Element) actionNode;
                        String name = action.getAttribute(ATTR_NAME);
                        if (MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                            mMediaPlayFromSearchFound = true;
                            return;
                        }
                    }
                }
            }
        }
        // Store context for potential later reporting
        if (mXmlContext == null) {
            mXmlContext = context;
        }
    }

    // ---- SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_BROWSER_SERVICE_COMPAT, MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (MEDIA_BROWSER_SERVICE_COMPAT.equals(qualifiedName)
                || MEDIA_BROWSER_SERVICE.equals(qualifiedName)) {
            return;
        }
        mMediaBrowserServiceFound = true;
        mMediaBrowserServiceClass = declaration;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        // Not used, but required by the interface
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mMediaBrowserServiceFound && !mMediaPlayFromSearchFound) {
            if (mMediaBrowserServiceClass != null && mXmlContext == null) {
                // Report on the Java class if we have no XML context
                // This shouldn't normally happen, but handle gracefully
            } else if (mXmlContext != null) {
                // Report in the manifest context
                mXmlContext.report(
                        MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                        mXmlContext.getDocument().getDocumentElement(),
                        mXmlContext.getLocation(mXmlContext.getDocument().getDocumentElement()),
                        "Missing `intent-filter` for action "
                                + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }
}