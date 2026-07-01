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
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    private static final String ID = "ManifestTypo";
    private static final int PRIORITY = 6;
    private static final String DESCRIPTION = "Looks for typos in Android manifest tag names";
    private static final String EXPLANATION =
            "The AndroidManifest.xml uses a tag name that is very similar to, "
                    + "but not exactly equal to, a known manifest tag. This is likely a "
                    + "typo and the entry may not behave as intended.";

    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Category.CORRECTNESS,
            PRIORITY,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "uses-permission",
            "permission",
            "permission-group",
            "permission-tree",
            "uses-sdk",
            "uses-feature",
            "supports-screens",
            "compatible-screens",
            "uses-configuration",
            "uses-library",
            "uses-native-library",
            "instrumentation",
            "provider",
            "receiver",
            "service",
            "intent-filter",
            "action",
            "category",
            "data",
            "meta-data",
            "grant-uri-permission",
            "layout",
            "profile",
            "overlay",
            "package",
            "original-package",
            "adopt-permissions",
            "permissionGroup",
            "restrict-update",
            "uses-permission-sdk-23",
            "eat-comment"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (tag == null || tag.isEmpty() || VALID_TAGS.contains(tag)) {
            return;
        }

        String closest = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String validTag : VALID_TAGS) {
            int distance = editDistance(tag, validTag);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = validTag;
            }
        }

        if (closest != null) {
            int threshold = tag.length() <= 4 ? 1 : 2;
            if (bestDistance <= threshold) {
                String message = String.format(
                        "Manifest tag `%1$s` looks like a misspelling of `%2$s`",
                        tag, closest);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
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
                char tc = t.charAt(j - 1);
                int cost = (sc == tc) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[n];
    }
}