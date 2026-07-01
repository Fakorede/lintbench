package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final String[] KNOWN_TAGS = new String[] {
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "service",
            "receiver",
            "provider",
            "intent-filter",
            "action",
            "category",
            "data",
            "meta-data",
            "uses-permission",
            "uses-permission-sdk-23",
            "uses-sdk",
            "uses-feature",
            "uses-library",
            "uses-native-library",
            "uses-static-library",
            "uses-split",
            "permission",
            "permission-group",
            "permission-tree",
            "grant-uri-permission",
            "path-permission",
            "instrumentation",
            "supports-screens",
            "compatible-screens",
            "screen",
            "supports-gl-texture",
            "queries",
            "intent",
            "package",
            "original-package",
            "protected-broadcast",
            "profileable",
            "restrict-update",
            "eat-comment"
    };

    private static final Set<String> KNOWN_TAG_SET = new HashSet<String>(Arrays.asList(KNOWN_TAGS));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"AndroidManifest.xml".equals(context.file.getName())) {
            return;
        }

        String tag = element.getTagName();
        if (tag.indexOf(':') != -1) {
            return;
        }

        if (KNOWN_TAG_SET.contains(tag)) {
            return;
        }

        String closest = findClosestKnownTag(tag);
        if (closest != null) {
            String message = String.format("Misspelled tag `%1$s`; did you mean `%2$s`?", tag, closest);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static String findClosestKnownTag(String tag) {
        String closest = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String known : KNOWN_TAGS) {
            int distance = editDistance(tag, known);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = known;
            }
        }

        if (closest != null && isCloseEnough(tag, closest, bestDistance)) {
            return closest;
        }
        return null;
    }

    private static boolean isCloseEnough(String unknown, String closest, int distance) {
        if (distance == 0) {
            return false;
        }
        int maxLength = Math.max(unknown.length(), closest.length());
        if (maxLength <= 4) {
            return distance <= 1;
        }
        if (maxLength <= 8) {
            return distance <= 2;
        }
        return distance <= 3;
    }

    private static int editDistance(String s, String t) {
        if (s.equals(t)) {
            return 0;
        }

        int n = s.length();
        int m = t.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[] previous = new int[m + 1];
        int[] current = new int[m + 1];

        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            current[0] = i;
            char sChar = s.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = sChar == t.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[m];
    }
}