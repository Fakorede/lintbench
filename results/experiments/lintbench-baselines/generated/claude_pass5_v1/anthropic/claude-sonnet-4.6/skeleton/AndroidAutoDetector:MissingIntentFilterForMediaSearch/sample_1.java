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
import com.android.tools.lint.detector.api.XmlContext;
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

public class AndroidAutoDetector extends Detector implements Detector.UastScannerInterface, Detector.XmlScannerInterface {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";
    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT_CLASS =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT_CLASS_X =
            "androidx.media.MediaBrowserServiceCompat";

    // Whether the manifest has a MEDIA_PLAY_FROM_SEARCH intent filter
    private boolean mHasMediaPlayFromSearch;

    // Whether the manifest has a MediaBrowserService (or compat)
    private boolean mHasMediaBrowserService;

    // The element to report the issue on (the service/activity missing the intent filter)
    private Element mMediaBrowserServiceElement;

    // The XmlContext for the manifest
    private XmlContext mXmlContext;

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "`intent-filter` for the action "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n\n"
                            + "To do this, add\n"
                            + "