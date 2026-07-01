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

/**
 * Detector for Android Auto voice search support.
 * Checks that if an intent-filter for onPlayFromSearch is declared,
 * the MediaSession.Callback subclass also overrides onPlayFromSearch.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner, ClassScanner {

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
            "`intent-filter` for the action `onPlayFromSearch`, " +
            "you also need to override and implement " +
            "`onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE, Scope.ALL_CLASS_FILES)
            ))
            .addMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String MEDIA_SESSION_CALLBACK_CLASS =
            "android/support/v4/media/session/MediaSessionCompat$Callback";

    private static final String MEDIA_SESSION_CALLBACK_CLASS2 =
            "android/media/session/MediaSession$Callback";

    private static final String ON_PLAY_FROM_SEARCH_METHOD = "onPlayFromSearch";

    private static final String ON_PLAY_FROM_SEARCH_DESC =
            "(Ljava/lang/String;Landroid/os/Bundle;)V";

    /** Whether the manifest declares the play from search intent filter */
    private boolean mHasPlayFromSearchIntentFilter;

    /** Location of the intent filter declaration for error reporting */
    private Location mIntentFilterLocation;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearchIntentFilter = false;
        mIntentFilterLocation = null;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("action");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH.equals(name)) {
            mHasPlayFromSearchIntentFilter = true;
            mIntentFilterLocation = context.getLocation(element);
        }
    }

    // ---- ClassScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_SESSION_CALLBACK_CLASS,
                MEDIA_SESSION_CALLBACK_CLASS2
        );
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        if (!mHasPlayFromSearchIntentFilter) {
            return;
        }

        // Check if this class overrides onPlayFromSearch
        List methods = classNode.methods;
        if (methods != null) {
            for (Object methodObj : methods) {
                MethodNode method = (MethodNode) methodObj;
                if (ON_PLAY_FROM_SEARCH_METHOD.equals(method.name)
                        && ON_PLAY_FROM_SEARCH_DESC.equals(method.desc)) {
                    // Found the override, no issue
                    return;
                }
            }
        }

        // The class extends MediaSession.Callback but doesn't override onPlayFromSearch
        Location location = context.getLocation(classNode);
        context.report(
                MISSING_ON_PLAY_FROM_SEARCH,
                location,
                "This class does not override `onPlayFromSearch` from " +
                "`MediaSession.Callback`; implement this method to support " +
                "voice searches on Android Auto"
        );
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Nothing needed here since we report in checkClass
    }
}