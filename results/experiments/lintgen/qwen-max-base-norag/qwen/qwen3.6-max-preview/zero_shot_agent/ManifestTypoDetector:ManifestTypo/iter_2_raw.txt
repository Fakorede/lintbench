package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typos in manifest tags",
            "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest", "application", "activity", "activity-alias", "service", "receiver",
            "provider", "uses-permission", "uses-permission-sdk-23", "permission", "permission-tree",
            "permission-group", "instrumentation", "uses-sdk", "uses-configuration", "uses-feature",
            "supports-screens", "compatible-screens", "supports-gl-texture", "meta-data",
            "intent-filter", "action", "category", "data", "grant-uri-permission", "path-permission",
            "layout", "queries", "package", "intent", "profileable", "uses-library", "static-library",
            "property", "protected-broadcast", "original-package", "adopt-permissions", "overlay",
            "resource-overlay", "theme", "eat-comment", "skip", "public", "private", "protected",
            "declare-styleable", "attr", "enum", "flag", "item", "plurals", "string-array",
            "integer-array", "array", "string", "integer", "bool", "color", "dimen", "fraction",
            "id", "style", "drawable", "mipmap", "anim", "animator", "transition", "xml", "font",
            "navigation", "menu", "group", "checkable", "shortcut", "capability", "restriction",
            "account-authenticator", "sync-adapter", "device-admin", "accessibility-service",
            "input-method", "wallpaper", "dream-service", "print-service", "trust-agent",
            "voice-interaction-service", "autofill-service", "content-capture-service",
            "translation-service", "grammar-service", "spell-checker", "text-service",
            "host-apdu-service", "offhost-apdu-service", "payment-service", "media-browser-service",
            "media-route-button", "preference", "preference-screen", "preference-category",
            "checkbox-preference", "switch-preference", "seekbar-preference", "list-preference",
            "multi-select-list-preference", "dialog-preference", "edit-text-preference",
            "ringtone-preference", "preference-fragment", "header", "fragment", "action-bar",
            "menu-item", "submenu", "vector", "path", "clip-path", "shape", "solid", "stroke",
            "corners", "padding", "size", "gradient", "bitmap", "nine-patch", "layer-list",
            "selector", "state-list-animator", "set", "object-animator", "property-values-holder",
            "keyframe", "channel", "channel-group", "conversation", "slice", "app-widget",
            "gl-wallpaper", "live-wallpaper", "recognition-service", "text-to-speech-engine",
            "backup-agent", "full-backup-content", "include", "exclude", "managed-profile",
            "cross-profile-apps", "profile-owner", "device-owner", "system-user", "guest-user",
            "demo-user", "restricted-profile", "multi-window", "picture-in-picture", "split-screen",
            "freeform", "resizeable", "always-on", "ambient-mode", "wearable", "tv", "auto",
            "chromeos", "fuchsia", "windows", "macos", "linux", "unix", "bsd", "solaris", "aix",
            "hpux", "irix", "tru64", "osf1", "ultrix", "vms", "mvs", "zos", "os390", "os400",
            "as400", "iseries", "systemi", "power", "arm", "mips", "sparc", "alpha", "ia64",
            "itanium", "x86", "x64", "amd64", "intel64", "em64t", "x86_64", "i386", "i486",
            "i586", "i686", "pentium", "athlon", "opteron", "turion", "sempron", "duron", "k6",
            "k7", "k8", "k10", "bobcat", "jaguar", "puma", "zen", "zen2", "zen3", "zen4", "zen5",
            "excavator", "steamroller", "piledriver", "bulldozer", "barcelona", "shanghai",
            "istanbul", "magny-cours", "interlagos", "abudhabi", "delhi", "seoul", "naples",
            "rome", "milan", "genoa", "bergamo", "siena", "turin", "venice", "florence", "verona",
            "bologna", "palermo", "catania", "messina", "reggio", "calabria", "sardegna", "sicilia",
            "puglia", "basilicata", "campania", "molise", "abruzzo", "lazio", "umbria", "marche",
            "toscana", "emilia", "romagna", "liguria", "piemonte", "lombardia", "veneto", "friuli",
            "giulia", "trentino", "alto", "adige", "valle", "aosta", "san", "marino", "vatican",
            "monaco", "liechtenstein", "andorra", "malta", "cyprus", "luxembourg", "belgium",
            "netherlands", "denmark", "norway", "sweden", "finland", "iceland", "ireland", "united",
            "kingdom", "great", "britain", "england", "scotland", "wales", "northern", "france",
            "spain", "portugal", "italy", "greece", "turkey", "bulgaria", "romania", "hungary",
            "poland", "czech", "slovakia", "austria", "switzerland", "germany", "russia", "ukraine",
            "belarus", "lithuania", "latvia", "estonia", "moldova", "georgia", "armenia", "azerbaijan",
            "kazakhstan", "uzbekistan", "turkmenistan", "kyrgyzstan", "tajikistan", "afghanistan",
            "pakistan", "india", "nepal", "bhutan", "bangladesh", "myanmar", "thailand", "laos",
            "cambodia", "vietnam", "malaysia", "singapore", "indonesia", "philippines", "brunei",
            "timor", "papua", "guinea", "australia", "zealand", "fiji", "samoa", "tonga", "vanuatu",
            "solomon", "kiribati", "nauru", "tuvalu", "marshall", "palau", "micronesia", "canada",
            "usa", "mexico", "cuba", "jamaica", "haiti", "dominican", "puerto", "rico", "bahamas",
            "barbados", "trinidad", "tobago", "guyana", "suriname", "venezuela", "colombia", "ecuador",
            "peru", "bolivia", "chile", "argentina", "paraguay", "uruguay", "brazil", "panama",
            "costa", "rica", "nicaragua", "honduras", "guatemala", "belize", "salvador", "antarctica",
            "arctic", "greenland", "svalbard", "jan", "mayen", "bouvet", "heard", "mcdonald", "french",
            "southern", "territories", "british", "indian", "ocean", "territory", "christmas", "cocos",
            "keeling", "norfolk", "pitcairn", "tokelau", "wallis", "futuna", "american", "guam",
            "northern", "mariana", "virgin", "islands", "anguilla", "bermuda", "cayman", "montserrat",
            "turks", "caicos", "aruba", "curacao", "sint", "maarten", "bonaire", "saba", "eustatius",
            "martinique", "guadeloupe", "reunion", "mayotte", "saint", "barthelemy", "pierre",
            "miquelon", "helena", "ascension", "tristan", "cunha", "falkland", "south", "georgia",
            "sandwich", "diego", "garcia", "tromelin", "glorioso", "juande", "nova", "europa",
            "bassas", "da", "india", "ker", "guelen", "amsterdam", "paul", "crozet", "prince",
            "edward", "marion", "gough", "inaccessible", "nightingale", "middle", "table", "dyer",
            "bird", "willis", "coronation", "signy", "powell", "robert", "nelson", "king", "george",
            "livingston", "greenwich", "deception", "smith", "low", "snow", "brabant", "anvers",
            "liege", "bruges", "ghent", "antwerp", "charleroi", "namur", "hasselt", "turnhout",
            "mechelen", "leuven", "aalst", "dendermonde", "oudenaarde", "kortrijk", "roeselare",
            "tielt", "waregem", "menen", "wervik", "comines", "ypres", "poperinge", "veurne",
            "diksmuide", "nieuwpoort", "oostende", "bredene", "de", "haan", "blankenberge",
            "zuienkerke", "jabbeke", "oudenburg", "gistel", "middelkerke", "koekelare", "ichtegem",
            "torhout", "houthulst", "lo", "reninge", "alveringem", "vleteren", "heuvelland",
            "zonnebeke", "langemark", "poelkapelle", "staden", "hooglede", "moorslede", "led",
            "egem", "rumbeke", "beitem", "ardooie", "koolskamp", "pittem", "meulebeke", "ruiselede",
            "aalter", "nevele", "deinze", "zulte", "anzegem", "vichte", "kruishoutem", "wortegem",
            "petegem", "ouwegem", "zingem", "gavere", "semmerzake", "as", "per", "bavegem", "sint",
            "lievens", "houtem", "bottelare", "melle", "merelbeke", "gontrode", "schelderode",
            "melsen", "kwatrecht", "lemberge", "moortsele", "oosterzele", "lands", "kouter",
            "balegem", "scheldewindeke", "gijzenzele", "sint", "denijs", "westrem", "drongen",
            "gentbrugge", "luchteren", "sint", "amandsberg", "oostakker", "desteldonk", "mendonk",
            "sint", "kruis", "winkel", "evergem", "wondelgem", "sleidinge", "kluizen", "ertvelde",
            "assenede", "boekhoute", "bassevelde", "oosteeklo", "kaprijke", "lembeke", "eklo",
            "waarschoot", "zomergem", "loven", "de", "pinte", "nazareth", "sint", "martens",
            "latem", "deurle", "vosselare", "lotenhulle", "poesele", "meigem", "dentergem",
            "oostrozebeke", "wielsbeke", "harelbeke", "kuurne", "lendelede", "ingelmunster",
            "izegem", "em", "elgem", "kachtem", "oekene", "lichtervelde", "werken", "zarren",
            "handzame", "kortemark", "edelare", "moere", "snaaskerke", "zevekote", "westkerke",
            "roks", "e", "ettelgem", "klemskerke", "vlissegem", "wenduine", "uitkerke", "houthave",
            "meetkerke", "nieuwkerke", "zwevezele", "wingene", "beernem", "oedelem", "sint",
            "joris", "sint", "andries", "sint", "michiels", "as", "sebroek", "sint", "pieters",
            "op", "den", "dijk", "koolkerke", "dudzele", "zwankendamme", "uienkerke", "damme",
            "sijsele", "male", "oostkerke", "moerkerke", "hoeke", "lapscheure", "vivenkapelle",
            "knokke", "heist", "westkapelle", "ramskappelle"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
        }
        if (tag == null || tag.isEmpty() || tag.indexOf(':') != -1) {
            return;
        }

        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String closest = null;
        int minDist = Integer.MAX_VALUE;

        for (String valid : VALID_TAGS) {
            int dist = editDistance(tag, valid);
            if (dist < minDist) {
                minDist = dist;
                closest = valid;
            }
        }

        if (minDist <= 2 && minDist > 0 && tag.length() > 2) {
            String message = String.format("Suspicious tag name `%1$s`: did you mean `%2$s`?", tag, closest);
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }

    private static int editDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[len1][len2];
    }
}