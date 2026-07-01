package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_INTENT;
import static com.android.xml.AndroidManifest.NODE_SERVICE;

public class AndroidAutoDetector extends Detector implements XmlScanner, ClassScanner {

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
            "`intent-filter` for the action `onPlayFromSearch`, " +
            "you also need to override and implement " +
            "`onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE)
            )
    ).addMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String MEDIA_SESSION_COMPAT_CALLBACK =
            "android/support/v4/media/session/MediaSessionCompat$Callback";

    private static final String MEDIA_SESSION_CALLBACK =
            "android/media/session/MediaSession$Callback";

    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    private static final String ON_PLAY_FROM_SEARCH_SIG =
            "(Ljava/lang/String;Landroid/os/Bundle;)V";

    /** Whether the manifest contains the play-from-search action */
    private boolean mHasPlayFromSearch;

    /** Location of the play-from-search action in the manifest */
    private Location mPlayFromSearchLocation;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearch = false;
        mPlayFromSearchLocation = null;
    }

    // ---- XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_ACTION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH.equals(name)) {
            // Check that this action is inside an intent-filter inside a service
            Node intentFilter = element.getParentNode();
            if (intentFilter != null && NODE_INTENT.equals(intentFilter.getLocalName())) {
                Node service = intentFilter.getParentNode();
                if (service != null && NODE_SERVICE.equals(service.getLocalName())) {
                    mHasPlayFromSearch = true;
                    mPlayFromSearchLocation = context.getLocation(element);
                }
            }
        }
    }

    // ---- ClassScanner ----

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_SESSION_COMPAT_CALLBACK,
                MEDIA_SESSION_CALLBACK
        );
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        if (!mHasPlayFromSearch) {
            return;
        }

        // Check if this class overrides onPlayFromSearch
        List methods = classNode.methods;
        if (methods != null) {
            for (Object methodObj : methods) {
                MethodNode method = (MethodNode) methodObj;
                if (ON_PLAY_FROM_SEARCH.equals(method.name)
                        && ON_PLAY_FROM_SEARCH_SIG.equals(method.desc)) {
                    // Found the override - no issue
                    return;
                }
            }
        }

        // The class extends the callback but doesn't override onPlayFromSearch
        Location location = context.getLocation(classNode);
        context.report(
                MISSING_ON_PLAY_FROM_SEARCH,
                location,
                "This class does not override `onPlayFromSearch` from " +
                "`MediaSession.Callback`. If you intend for your app to support " +
                "voice searches on Android Auto, you need to implement this method."
        );
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // If we found the intent-filter action but never found a class that overrides
        // onPlayFromSearch, we can report on the manifest location as a fallback.
        // (The class check above handles the case when we can analyze class files.)
    }
}