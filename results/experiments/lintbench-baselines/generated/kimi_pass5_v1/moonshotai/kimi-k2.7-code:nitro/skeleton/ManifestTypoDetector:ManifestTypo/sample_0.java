package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_MANIFEST = "AndroidManifest.xml";

    private static final Set<String> KNOWN_TAGS;
    static {
        Set<String> set = new HashSet<>(Arrays.asList(
                "action",
                "activity",
                "activity-alias",
                "application",
                "attribution",
                "category",
                "compatible-screens",
                "data",
                "grant-uri-permission",
                "instrumentation",
                "intent",
                "intent-filter",
                "manifest",
                "meta-data",
                "original-package",
                "package",
                "path-permission",
                "permission",
                "permission-group",
                "permission-tree",
                "profileable",
                "property",
                "provider",
                "queries",
                "receiver",
                "screen",
                "service",
                "supports-gl-texture",
                "supports-screens",
                "uses-configuration",
                "uses-feature",
                "uses-library",
                "uses-native-library",
                "uses-permission",
                "uses-permission-sdk-23"));
        KNOWN_TAGS = Collections.unmodifiableSet(set);
    }

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the AndroidManifest.xml file and flags any tags "
                            + "that are not recognized as standard Android manifest tags but "
                            + "closely resemble one. Misspelled tags are ignored by the framework, "
                            + "which can cause missing permissions, components, or features.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!ANDROID_MANIFEST.equals(context.file.getName())) {
            return;
        }

        String tag = element.getTagName();
        if (tag.isEmpty() || tag.indexOf(':') != -1) {
            return;
        }

        String normalized = tag.toLowerCase(Locale.US);
        if (KNOWN_TAGS.contains(normalized)) {
            return;
        }

        String closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (String known : KNOWN_TAGS) {
            int distance = editDistance(normalized, known);
            if (distance < minDistance) {
                minDistance = distance;
                closest = known;
            }
        }

        if (closest != null && isLikelyTypo(normalized, closest, minDistance)) {
            String message = String.format(
                    "Misspelled tag `%1$s`; did you mean `%2$s`?", tag, closest);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static boolean isLikelyTypo(String tag, String closest, int distance) {
        if (distance <= 0 || distance > 2) {
            return false;
        }
        return tag.length() > 2;
    }

    private static int editDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        if (m == 0) {
            return n;
        }
        if (n == 0) {
            return m;
        }

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char sc = s.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = sc == t.charAt(j - 1) ? 0 : 1;
                int deletion = curr[j - 1] + 1;
                int insertion = prev[j] + 1;
                int substitution = prev[j - 1] + cost;
                int min = deletion < insertion ? deletion : insertion;
                curr[j] = min < substitution ? min : substitution;
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[n];
    }
}