package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
                    EnumSet.of(Scope.MANIFEST, Scope.CLASS_FILE)
            )
    ).addMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String MEDIA_SESSION_CALLBACK_CLASS =
            "android/support/v4/media/session/MediaSessionCompat$Callback";
    private static final String MEDIA_SESSION_CALLBACK_CLASS2 =
            "android/media/session/MediaSession$Callback";
    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String ON_PLAY_FROM_SEARCH_SIG =
            "(Ljava/lang/String;Landroid/os/Bundle;)V";

    private boolean mHasPlayFromSearch = false;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("action");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH.equals(name)) {
            mHasPlayFromSearch = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_SESSION_CALLBACK_CLASS,
                MEDIA_SESSION_CALLBACK_CLASS2
        );
    }

    @Override
    public void checkClass(ClassContext context, ClassNode classNode) {
        if (!mHasPlayFromSearch) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<MethodNode> methods = classNode.methods;
        if (methods != null) {
            for (MethodNode method : methods) {
                if (ON_PLAY_FROM_SEARCH.equals(method.name)
                        && ON_PLAY_FROM_SEARCH_SIG.equals(method.desc)) {
                    return;
                }
            }
        }

        context.report(
                MISSING_ON_PLAY_FROM_SEARCH,
                context.getLocation(classNode),
                "This class does not override `onPlayFromSearch` from " +
                "`MediaSession.Callback`. The `onPlayFromSearch` method needs to be " +
                "overridden to support voice searches on Android Auto."
        );
    }
}