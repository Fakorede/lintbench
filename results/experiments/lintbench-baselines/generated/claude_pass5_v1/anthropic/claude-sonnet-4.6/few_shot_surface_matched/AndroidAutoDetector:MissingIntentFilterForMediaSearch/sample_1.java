package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_MEDIA_ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_ACTIVITY = "activity";
    private static final String ATTR_ANDROID_NAME = "android:name";

    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";

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
                                    + "<intent-filter>\n"
                                    + "    <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />\n"
                                    + "</intent-filter>\n"
                                    + "```\n"
                                    + "to your `<activity>` or `<service>`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#support_voice");

    private boolean mHasMediaPlayFromSearchIntentFilter = false;
    private boolean mMediaBrowserServiceFound = false;
    private Element mMediaBrowserServiceElement = null;
    private XmlContext mXmlContext = null;

    public AndroidAutoDetector() {}

    // XmlScanner methods

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return false;
    }

    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.Context context,
            @NonNull java.io.File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SERVICE, TAG_ACTIVITY);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasMediaPlayFromSearchIntentFilter = false;
        mMediaBrowserServiceFound = false;
        mMediaBrowserServiceElement = null;
        mXmlContext = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this service/activity contains a MEDIA_PLAY_FROM_SEARCH intent-filter
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
                        String name = action.getAttribute(ATTR_ANDROID_NAME);
                        if (ANDROID_MEDIA_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                            mHasMediaPlayFromSearchIntentFilter = true;
                            return;
                        }
                    }
                }
            }
        }

        // Check if this is a MediaBrowserService
        if (TAG_SERVICE.equals(element.getTagName())) {
            String name = element.getAttribute(ATTR_ANDROID_NAME);
            if (name != null && !name.isEmpty()) {
                // Track this service element for potential reporting
                if (!mMediaBrowserServiceFound) {
                    mMediaBrowserServiceElement = element;
                    mXmlContext = context;
                }
            }
        }
    }

    // SourceCodeScanner methods

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_BROWSER_SERVICE_COMPAT, MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mMediaBrowserServiceFound = true;
        // Check if we already found the intent filter
        if (!mHasMediaPlayFromSearchIntentFilter) {
            // Report on the class declaration if no intent filter found
            // We'll do final reporting in afterCheckRootProject via the XML context if available
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        // Not used in this detector
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mMediaBrowserServiceFound
                && !mHasMediaPlayFromSearchIntentFilter
                && mXmlContext != null
                && mMediaBrowserServiceElement != null) {
            mXmlContext.report(
                    MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                    mMediaBrowserServiceElement,
                    mXmlContext.getLocation(mMediaBrowserServiceElement),
                    "To support voice searches on Android Auto, register an "
                            + "`intent-filter` for the action "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
        }
    }
}