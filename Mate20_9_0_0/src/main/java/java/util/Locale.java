package java.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectInputStream.GetField;
import java.io.ObjectOutputStream;
import java.io.ObjectOutputStream.PutField;
import java.io.ObjectStreamException;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.text.MessageFormat;
import libcore.icu.ICU;
import sun.security.x509.PolicyInformation;
import sun.util.locale.BaseLocale;
import sun.util.locale.InternalLocaleBuilder;
import sun.util.locale.LanguageTag;
import sun.util.locale.LocaleExtensions;
import sun.util.locale.LocaleMatcher;
import sun.util.locale.LocaleObjectCache;
import sun.util.locale.LocaleSyntaxException;
import sun.util.locale.LocaleUtils;
import sun.util.locale.ParseStatus;

public final class Locale implements Cloneable, Serializable {
    static final /* synthetic */ boolean $assertionsDisabled = false;
    public static final Locale CANADA = createConstant("en", "CA");
    public static final Locale CANADA_FRENCH = createConstant("fr", "CA");
    public static final Locale CHINA = SIMPLIFIED_CHINESE;
    public static final Locale CHINESE = createConstant("zh", "");
    private static final int DISPLAY_COUNTRY = 1;
    private static final int DISPLAY_LANGUAGE = 0;
    private static final int DISPLAY_SCRIPT = 3;
    private static final int DISPLAY_VARIANT = 2;
    public static final Locale ENGLISH = createConstant("en", "");
    public static final Locale FRANCE = createConstant("fr", "FR");
    public static final Locale FRENCH = createConstant("fr", "");
    public static final Locale GERMAN = createConstant("de", "");
    public static final Locale GERMANY = createConstant("de", "DE");
    public static final Locale ITALIAN = createConstant("it", "");
    public static final Locale ITALY = createConstant("it", "IT");
    public static final Locale JAPAN = createConstant("ja", "JP");
    public static final Locale JAPANESE = createConstant("ja", "");
    public static final Locale KOREA = createConstant("ko", "KR");
    public static final Locale KOREAN = createConstant("ko", "");
    private static final Cache LOCALECACHE = new Cache();
    public static final Locale PRC = SIMPLIFIED_CHINESE;
    public static final char PRIVATE_USE_EXTENSION = 'x';
    public static final Locale ROOT = createConstant("", "");
    public static final Locale SIMPLIFIED_CHINESE = createConstant("zh", "CN");
    public static final Locale TAIWAN = TRADITIONAL_CHINESE;
    public static final Locale TRADITIONAL_CHINESE = createConstant("zh", "TW");
    public static final Locale UK = createConstant("en", "GB");
    private static final String UNDETERMINED_LANGUAGE = "und";
    public static final char UNICODE_LOCALE_EXTENSION = 'u';
    public static final Locale US = createConstant("en", "US");
    private static volatile Locale defaultDisplayLocale = null;
    private static volatile Locale defaultFormatLocale = null;
    private static volatile String[] isoCountries = null;
    private static volatile String[] isoLanguages = null;
    private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[]{new ObjectStreamField("language", String.class), new ObjectStreamField("country", String.class), new ObjectStreamField("variant", String.class), new ObjectStreamField("hashcode", Integer.TYPE), new ObjectStreamField("script", String.class), new ObjectStreamField("extensions", String.class)};
    static final long serialVersionUID = 9149081749638150636L;
    private transient BaseLocale baseLocale;
    private volatile transient int hashCodeValue;
    private volatile transient String languageTag;
    private transient LocaleExtensions localeExtensions;

    public static final class Builder {
        private final InternalLocaleBuilder localeBuilder = new InternalLocaleBuilder();

        public Builder setLocale(Locale locale) {
            try {
                this.localeBuilder.setLocale(locale.baseLocale, locale.localeExtensions);
                return this;
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
        }

        public Builder setLanguageTag(String languageTag) {
            ParseStatus sts = new ParseStatus();
            LanguageTag tag = LanguageTag.parse(languageTag, sts);
            if (sts.isError()) {
                throw new IllformedLocaleException(sts.getErrorMessage(), sts.getErrorIndex());
            }
            this.localeBuilder.setLanguageTag(tag);
            return this;
        }

        public Builder setLanguage(String language) {
            try {
                this.localeBuilder.setLanguage(language);
                return this;
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
        }

        public Builder setScript(String script) {
            try {
                this.localeBuilder.setScript(script);
                return this;
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
        }

        public Builder setRegion(String region) {
            try {
                this.localeBuilder.setRegion(region);
                return this;
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
        }

        public Builder setVariant(String variant) {
            try {
                this.localeBuilder.setVariant(variant);
                return this;
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
        }

        public Builder setExtension(char key, String value) {
            try {
                this.localeBuilder.setExtension(key, value);
                return this;
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
        }

        public Builder setUnicodeLocaleKeyword(String key, String type) {
            try {
                this.localeBuilder.setUnicodeLocaleKeyword(key, type);
                return this;
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
        }

        public Builder addUnicodeLocaleAttribute(String attribute) {
            try {
                this.localeBuilder.addUnicodeLocaleAttribute(attribute);
                return this;
            } catch (LocaleSyntaxException e) {
                throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
            }
        }

        public Builder removeUnicodeLocaleAttribute(String attribute) {
            if (attribute != null) {
                try {
                    this.localeBuilder.removeUnicodeLocaleAttribute(attribute);
                    return this;
                } catch (LocaleSyntaxException e) {
                    throw new IllformedLocaleException(e.getMessage(), e.getErrorIndex());
                }
            }
            throw new NullPointerException("attribute == null");
        }

        public Builder clear() {
            this.localeBuilder.clear();
            return this;
        }

        public Builder clearExtensions() {
            this.localeBuilder.clearExtensions();
            return this;
        }

        public Locale build() {
            BaseLocale baseloc = this.localeBuilder.getBaseLocale();
            LocaleExtensions extensions = this.localeBuilder.getLocaleExtensions();
            if (extensions == null && baseloc.getVariant().length() > 0) {
                extensions = Locale.getCompatibilityExtensions(baseloc.getLanguage(), baseloc.getScript(), baseloc.getRegion(), baseloc.getVariant());
            }
            return Locale.getInstance(baseloc, extensions);
        }
    }

    public static final class LanguageRange {
        public static final double MAX_WEIGHT = 1.0d;
        public static final double MIN_WEIGHT = 0.0d;
        private volatile int hash;
        private final String range;
        private final double weight;

        public LanguageRange(String range) {
            this(range, 1.0d);
        }

        public LanguageRange(String range, double weight) {
            this.hash = 0;
            if (range == null) {
                throw new NullPointerException();
            } else if (weight < 0.0d || weight > 1.0d) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("weight=");
                stringBuilder.append(weight);
                throw new IllegalArgumentException(stringBuilder.toString());
            } else {
                range = range.toLowerCase();
                boolean isIllFormed = Locale.$assertionsDisabled;
                String[] subtags = range.split(LanguageTag.SEP);
                int i = 1;
                if (isSubtagIllFormed(subtags[0], true) || range.endsWith(LanguageTag.SEP)) {
                    isIllFormed = true;
                } else {
                    while (true) {
                        int i2 = i;
                        if (i2 >= subtags.length) {
                            break;
                        } else if (isSubtagIllFormed(subtags[i2], Locale.$assertionsDisabled)) {
                            isIllFormed = true;
                            break;
                        } else {
                            i = i2 + 1;
                        }
                    }
                }
                if (isIllFormed) {
                    StringBuilder stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("range=");
                    stringBuilder2.append(range);
                    throw new IllegalArgumentException(stringBuilder2.toString());
                }
                this.range = range;
                this.weight = weight;
            }
        }

        private static boolean isSubtagIllFormed(String subtag, boolean isFirstSubtag) {
            if (subtag.equals("") || subtag.length() > 8) {
                return true;
            }
            if (subtag.equals("*")) {
                return Locale.$assertionsDisabled;
            }
            char[] charArray = subtag.toCharArray();
            if (isFirstSubtag) {
                for (char c : charArray) {
                    if (c < 'a' || c > 'z') {
                        return true;
                    }
                }
            } else {
                for (char c2 : charArray) {
                    if (c2 < '0' || ((c2 > '9' && c2 < 'a') || c2 > 'z')) {
                        return true;
                    }
                }
            }
            return Locale.$assertionsDisabled;
        }

        public String getRange() {
            return this.range;
        }

        public double getWeight() {
            return this.weight;
        }

        public static List<LanguageRange> parse(String ranges) {
            return LocaleMatcher.parse(ranges);
        }

        public static List<LanguageRange> parse(String ranges, Map<String, List<String>> map) {
            return mapEquivalents(parse(ranges), map);
        }

        public static List<LanguageRange> mapEquivalents(List<LanguageRange> priorityList, Map<String, List<String>> map) {
            return LocaleMatcher.mapEquivalents(priorityList, map);
        }

        public int hashCode() {
            if (this.hash == 0) {
                int result = (37 * 17) + this.range.hashCode();
                long bitsWeight = Double.doubleToLongBits(this.weight);
                this.hash = (37 * result) + ((int) ((bitsWeight >>> 32) ^ bitsWeight));
            }
            return this.hash;
        }

        public boolean equals(Object obj) {
            boolean z = true;
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LanguageRange)) {
                return Locale.$assertionsDisabled;
            }
            LanguageRange other = (LanguageRange) obj;
            if (!(this.hash == other.hash && this.range.equals(other.range) && this.weight == other.weight)) {
                z = Locale.$assertionsDisabled;
            }
            return z;
        }
    }

    private static final class LocaleKey {
        private final BaseLocale base;
        private final LocaleExtensions exts;
        private final int hash;

        private LocaleKey(BaseLocale baseLocale, LocaleExtensions extensions) {
            this.base = baseLocale;
            this.exts = extensions;
            int h = this.base.hashCode();
            if (this.exts != null) {
                h ^= this.exts.hashCode();
            }
            this.hash = h;
        }

        public boolean equals(Object obj) {
            boolean z = true;
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LocaleKey)) {
                return Locale.$assertionsDisabled;
            }
            LocaleKey other = (LocaleKey) obj;
            if (this.hash != other.hash || !this.base.equals(other.base)) {
                return Locale.$assertionsDisabled;
            }
            if (this.exts != null) {
                return this.exts.equals(other.exts);
            }
            if (other.exts != null) {
                z = Locale.$assertionsDisabled;
            }
            return z;
        }

        public int hashCode() {
            return this.hash;
        }
    }

    private static class NoImagePreloadHolder {
        public static volatile Locale defaultLocale = Locale.initDefault();

        private NoImagePreloadHolder() {
        }
    }

    private static class Cache extends LocaleObjectCache<LocaleKey, Locale> {
        private Cache() {
        }

        protected Locale createObject(LocaleKey key) {
            return new Locale(key.base, key.exts, null);
        }
    }

    public enum Category {
        DISPLAY("user.language.display", "user.script.display", "user.country.display", "user.variant.display"),
        FORMAT("user.language.format", "user.script.format", "user.country.format", "user.variant.format");
        
        final String countryKey;
        final String languageKey;
        final String scriptKey;
        final String variantKey;

        private Category(String languageKey, String scriptKey, String countryKey, String variantKey) {
            this.languageKey = languageKey;
            this.scriptKey = scriptKey;
            this.countryKey = countryKey;
            this.variantKey = variantKey;
        }
    }

    public enum FilteringMode {
        AUTOSELECT_FILTERING,
        EXTENDED_FILTERING,
        IGNORE_EXTENDED_RANGES,
        MAP_EXTENDED_RANGES,
        REJECT_EXTENDED_RANGES
    }

    private Locale(BaseLocale baseLocale, LocaleExtensions extensions) {
        this.hashCodeValue = 0;
        this.baseLocale = baseLocale;
        this.localeExtensions = extensions;
    }

    public Locale(String language, String country, String variant) {
        this.hashCodeValue = 0;
        if (language == null || country == null || variant == null) {
            throw new NullPointerException();
        }
        this.baseLocale = BaseLocale.getInstance(convertOldISOCodes(language), "", country, variant);
        this.localeExtensions = getCompatibilityExtensions(language, "", country, variant);
    }

    public Locale(String language, String country) {
        this(language, country, "");
    }

    public Locale(String language) {
        this(language, "", "");
    }

    private static Locale createConstant(String lang, String country) {
        return getInstance(BaseLocale.createInstance(lang, country), null);
    }

    static Locale getInstance(String language, String country, String variant) {
        return getInstance(language, "", country, variant, null);
    }

    static Locale getInstance(String language, String script, String country, String variant, LocaleExtensions extensions) {
        if (language == null || script == null || country == null || variant == null) {
            throw new NullPointerException();
        }
        if (extensions == null) {
            extensions = getCompatibilityExtensions(language, script, country, variant);
        }
        return getInstance(BaseLocale.getInstance(language, script, country, variant), extensions);
    }

    static Locale getInstance(BaseLocale baseloc, LocaleExtensions extensions) {
        return (Locale) LOCALECACHE.get(new LocaleKey(baseloc, extensions));
    }

    public static Locale getDefault() {
        return NoImagePreloadHolder.defaultLocale;
    }

    public static Locale getDefault(Category category) {
        switch (category) {
            case DISPLAY:
                if (defaultDisplayLocale == null) {
                    synchronized (Locale.class) {
                        if (defaultDisplayLocale == null) {
                            defaultDisplayLocale = initDefault(category);
                        }
                    }
                }
                return defaultDisplayLocale;
            case FORMAT:
                if (defaultFormatLocale == null) {
                    synchronized (Locale.class) {
                        if (defaultFormatLocale == null) {
                            defaultFormatLocale = initDefault(category);
                        }
                    }
                }
                return defaultFormatLocale;
            default:
                return getDefault();
        }
    }

    public static Locale initDefault() {
        String languageTag = System.getProperty("user.locale", "");
        if (!languageTag.isEmpty()) {
            return forLanguageTag(languageTag);
        }
        String country;
        String variant;
        String script;
        String language = System.getProperty("user.language", "en");
        String region = System.getProperty("user.region");
        if (region != null) {
            int i = region.indexOf(95);
            if (i >= 0) {
                country = region.substring(null, i);
                variant = region.substring(i + 1);
            } else {
                country = region;
                variant = "";
            }
            script = "";
        } else {
            script = System.getProperty("user.script", "");
            country = System.getProperty("user.country", "");
            variant = System.getProperty("user.variant", "");
        }
        return getInstance(language, script, country, variant, null);
    }

    private static Locale initDefault(Category category) {
        Locale defaultLocale = NoImagePreloadHolder.defaultLocale;
        return getInstance(System.getProperty(category.languageKey, defaultLocale.getLanguage()), System.getProperty(category.scriptKey, defaultLocale.getScript()), System.getProperty(category.countryKey, defaultLocale.getCountry()), System.getProperty(category.variantKey, defaultLocale.getVariant()), null);
    }

    public static synchronized void setDefault(Locale newLocale) {
        synchronized (Locale.class) {
            setDefault(Category.DISPLAY, newLocale);
            setDefault(Category.FORMAT, newLocale);
            NoImagePreloadHolder.defaultLocale = newLocale;
            ICU.setDefaultLocale(newLocale.toLanguageTag());
        }
    }

    public static synchronized void setDefault(Category category, Locale newLocale) {
        synchronized (Locale.class) {
            if (category == null) {
                throw new NullPointerException("Category cannot be NULL");
            } else if (newLocale != null) {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null) {
                    sm.checkPermission(new PropertyPermission("user.language", "write"));
                }
                switch (category) {
                    case DISPLAY:
                        defaultDisplayLocale = newLocale;
                        break;
                    case FORMAT:
                        defaultFormatLocale = newLocale;
                        break;
                    default:
                        break;
                }
            } else {
                throw new NullPointerException("Can't set default locale to NULL");
            }
        }
    }

    public static Locale[] getAvailableLocales() {
        return ICU.getAvailableLocales();
    }

    public static String[] getISOCountries() {
        return ICU.getISOCountries();
    }

    public static String[] getISOLanguages() {
        return ICU.getISOLanguages();
    }

    public String getLanguage() {
        return this.baseLocale.getLanguage();
    }

    public String getScript() {
        return this.baseLocale.getScript();
    }

    public String getCountry() {
        return this.baseLocale.getRegion();
    }

    public String getVariant() {
        return this.baseLocale.getVariant();
    }

    public boolean hasExtensions() {
        return this.localeExtensions != null ? true : $assertionsDisabled;
    }

    public Locale stripExtensions() {
        return hasExtensions() ? getInstance(this.baseLocale, null) : this;
    }

    public String getExtension(char key) {
        if (LocaleExtensions.isValidKey(key)) {
            return hasExtensions() ? this.localeExtensions.getExtensionValue(Character.valueOf(key)) : null;
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Ill-formed extension key: ");
            stringBuilder.append(key);
            throw new IllegalArgumentException(stringBuilder.toString());
        }
    }

    public Set<Character> getExtensionKeys() {
        if (hasExtensions()) {
            return this.localeExtensions.getKeys();
        }
        return Collections.emptySet();
    }

    public Set<String> getUnicodeLocaleAttributes() {
        if (hasExtensions()) {
            return this.localeExtensions.getUnicodeLocaleAttributes();
        }
        return Collections.emptySet();
    }

    public String getUnicodeLocaleType(String key) {
        if (isUnicodeExtensionKey(key)) {
            return hasExtensions() ? this.localeExtensions.getUnicodeLocaleType(key) : null;
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Ill-formed Unicode locale key: ");
            stringBuilder.append(key);
            throw new IllegalArgumentException(stringBuilder.toString());
        }
    }

    public Set<String> getUnicodeLocaleKeys() {
        if (this.localeExtensions == null) {
            return Collections.emptySet();
        }
        return this.localeExtensions.getUnicodeLocaleKeys();
    }

    BaseLocale getBaseLocale() {
        return this.baseLocale;
    }

    LocaleExtensions getLocaleExtensions() {
        return this.localeExtensions;
    }

    public final String toString() {
        int length = this.baseLocale.getLanguage().length();
        boolean e = $assertionsDisabled;
        boolean l = length != 0 ? true : $assertionsDisabled;
        boolean s = this.baseLocale.getScript().length() != 0 ? true : $assertionsDisabled;
        boolean r = this.baseLocale.getRegion().length() != 0 ? true : $assertionsDisabled;
        boolean v = this.baseLocale.getVariant().length() != 0 ? true : $assertionsDisabled;
        if (!(this.localeExtensions == null || this.localeExtensions.getID().length() == 0)) {
            e = true;
        }
        StringBuilder result = new StringBuilder(this.baseLocale.getLanguage());
        if (r || (l && (v || s || e))) {
            result.append('_');
            result.append(this.baseLocale.getRegion());
        }
        if (v && (l || r)) {
            result.append('_');
            result.append(this.baseLocale.getVariant());
        }
        if (s && (l || r)) {
            result.append("_#");
            result.append(this.baseLocale.getScript());
        }
        if (e && (l || r)) {
            result.append('_');
            if (!s) {
                result.append('#');
            }
            result.append(this.localeExtensions.getID());
        }
        return result.toString();
    }

    public String toLanguageTag() {
        if (this.languageTag != null) {
            return this.languageTag;
        }
        LanguageTag tag = LanguageTag.parseLocale(this.baseLocale, this.localeExtensions);
        StringBuilder buf = new StringBuilder();
        String subtag = tag.getLanguage();
        if (subtag.length() > 0) {
            buf.append(LanguageTag.canonicalizeLanguage(subtag));
        }
        subtag = tag.getScript();
        if (subtag.length() > 0) {
            buf.append(LanguageTag.SEP);
            buf.append(LanguageTag.canonicalizeScript(subtag));
        }
        subtag = tag.getRegion();
        if (subtag.length() > 0) {
            buf.append(LanguageTag.SEP);
            buf.append(LanguageTag.canonicalizeRegion(subtag));
        }
        for (String s : tag.getVariants()) {
            buf.append(LanguageTag.SEP);
            buf.append(s);
        }
        for (String s2 : tag.getExtensions()) {
            buf.append(LanguageTag.SEP);
            buf.append(LanguageTag.canonicalizeExtension(s2));
        }
        subtag = tag.getPrivateuse();
        if (subtag.length() > 0) {
            if (buf.length() > 0) {
                buf.append(LanguageTag.SEP);
            }
            buf.append(LanguageTag.PRIVATEUSE);
            buf.append(LanguageTag.SEP);
            buf.append(subtag);
        }
        String langTag = buf.toString();
        synchronized (this) {
            if (this.languageTag == null) {
                this.languageTag = langTag;
            }
        }
        return this.languageTag;
    }

    public static Locale forLanguageTag(String languageTag) {
        LanguageTag tag = LanguageTag.parse(languageTag, null);
        InternalLocaleBuilder bldr = new InternalLocaleBuilder();
        bldr.setLanguageTag(tag);
        BaseLocale base = bldr.getBaseLocale();
        LocaleExtensions exts = bldr.getLocaleExtensions();
        if (exts == null && base.getVariant().length() > 0) {
            exts = getCompatibilityExtensions(base.getLanguage(), base.getScript(), base.getRegion(), base.getVariant());
        }
        return getInstance(base, exts);
    }

    public String getISO3Language() throws MissingResourceException {
        String lang = this.baseLocale.getLanguage();
        if (lang.length() == 3) {
            return lang;
        }
        if (lang.isEmpty()) {
            return "";
        }
        String language3 = ICU.getISO3Language(lang);
        if (lang.isEmpty() || !language3.isEmpty()) {
            return language3;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Couldn't find 3-letter language code for ");
        stringBuilder.append(lang);
        String stringBuilder2 = stringBuilder.toString();
        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append("FormatData_");
        stringBuilder3.append(toString());
        throw new MissingResourceException(stringBuilder2, stringBuilder3.toString(), "ShortLanguage");
    }

    public String getISO3Country() throws MissingResourceException {
        String region = this.baseLocale.getRegion();
        if (region.length() == 3) {
            return this.baseLocale.getRegion();
        }
        if (region.isEmpty()) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("en-");
        stringBuilder.append(region);
        String country3 = ICU.getISO3Country(stringBuilder.toString());
        if (region.isEmpty() || !country3.isEmpty()) {
            return country3;
        }
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append("Couldn't find 3-letter country code for ");
        stringBuilder2.append(this.baseLocale.getRegion());
        String stringBuilder3 = stringBuilder2.toString();
        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append("FormatData_");
        stringBuilder4.append(toString());
        throw new MissingResourceException(stringBuilder3, stringBuilder4.toString(), "ShortCountry");
    }

    public final String getDisplayLanguage() {
        return getDisplayLanguage(getDefault(Category.DISPLAY));
    }

    public String getDisplayLanguage(Locale locale) {
        String languageCode = this.baseLocale.getLanguage();
        if (languageCode.isEmpty()) {
            return "";
        }
        if ("und".equals(normalizeAndValidateLanguage(languageCode, null))) {
            return languageCode;
        }
        String result = ICU.getDisplayLanguage(this, locale);
        if (result == null) {
            result = ICU.getDisplayLanguage(this, getDefault());
        }
        return result;
    }

    private static String normalizeAndValidateLanguage(String language, boolean strict) {
        if (language == null || language.isEmpty()) {
            return "";
        }
        String lowercaseLanguage = language.toLowerCase(ROOT);
        if (isValidBcp47Alpha(lowercaseLanguage, 2, 3)) {
            return lowercaseLanguage;
        }
        if (!strict) {
            return "und";
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Invalid language: ");
        stringBuilder.append(language);
        throw new IllformedLocaleException(stringBuilder.toString());
    }

    private static boolean isAsciiAlphaNum(String string) {
        for (int i = 0; i < string.length(); i++) {
            char character = string.charAt(i);
            if ((character < 'a' || character > 'z') && ((character < 'A' || character > 'Z') && (character < '0' || character > '9'))) {
                return $assertionsDisabled;
            }
        }
        return true;
    }

    public String getDisplayScript() {
        return getDisplayScript(getDefault(Category.DISPLAY));
    }

    public String getDisplayScript(Locale inLocale) {
        if (this.baseLocale.getScript().isEmpty()) {
            return "";
        }
        String result = ICU.getDisplayScript(this, inLocale);
        if (result == null) {
            result = ICU.getDisplayScript(this, getDefault(Category.DISPLAY));
        }
        return result;
    }

    public final String getDisplayCountry() {
        return getDisplayCountry(getDefault(Category.DISPLAY));
    }

    public String getDisplayCountry(Locale locale) {
        String countryCode = this.baseLocale.getRegion();
        if (countryCode.isEmpty()) {
            return "";
        }
        if (normalizeAndValidateRegion(countryCode, null).isEmpty()) {
            return countryCode;
        }
        String result = ICU.getDisplayCountry(this, locale);
        if (result == null) {
            result = ICU.getDisplayCountry(this, getDefault());
        }
        return result;
    }

    private static String normalizeAndValidateRegion(String region, boolean strict) {
        if (region == null || region.isEmpty()) {
            return "";
        }
        String uppercaseRegion = region.toUpperCase(ROOT);
        if (isValidBcp47Alpha(uppercaseRegion, 2, 2) || isUnM49AreaCode(uppercaseRegion)) {
            return uppercaseRegion;
        }
        if (!strict) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Invalid region: ");
        stringBuilder.append(region);
        throw new IllformedLocaleException(stringBuilder.toString());
    }

    private static boolean isValidBcp47Alpha(String string, int lowerBound, int upperBound) {
        int length = string.length();
        if (length < lowerBound || length > upperBound) {
            return $assertionsDisabled;
        }
        for (int i = 0; i < length; i++) {
            char character = string.charAt(i);
            if ((character < 'a' || character > 'z') && (character < 'A' || character > 'Z')) {
                return $assertionsDisabled;
            }
        }
        return true;
    }

    private static boolean isUnM49AreaCode(String code) {
        if (code.length() != 3) {
            return $assertionsDisabled;
        }
        for (int i = 0; i < 3; i++) {
            char character = code.charAt(i);
            if (character < '0' || character > '9') {
                return $assertionsDisabled;
            }
        }
        return true;
    }

    public final String getDisplayVariant() {
        return getDisplayVariant(getDefault(Category.DISPLAY));
    }

    public String getDisplayVariant(Locale inLocale) {
        String variantCode = this.baseLocale.getVariant();
        if (variantCode.isEmpty()) {
            return "";
        }
        try {
            normalizeAndValidateVariant(variantCode);
            String result = ICU.getDisplayVariant(this, inLocale);
            if (result == null) {
                result = ICU.getDisplayVariant(this, getDefault());
            }
            if (result.isEmpty()) {
                return variantCode;
            }
            return result;
        } catch (IllformedLocaleException e) {
            return variantCode;
        }
    }

    private static String normalizeAndValidateVariant(String variant) {
        if (variant == null || variant.isEmpty()) {
            return "";
        }
        String normalizedVariant = variant.replace('-', '_');
        String[] subTags = normalizedVariant.split(BaseLocale.SEP);
        int length = subTags.length;
        int i = 0;
        while (i < length) {
            if (isValidVariantSubtag(subTags[i])) {
                i++;
            } else {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Invalid variant: ");
                stringBuilder.append(variant);
                throw new IllformedLocaleException(stringBuilder.toString());
            }
        }
        return normalizedVariant;
    }

    private static boolean isValidVariantSubtag(String subTag) {
        if (subTag.length() < 5 || subTag.length() > 8) {
            if (subTag.length() == 4) {
                char firstChar = subTag.charAt(0);
                return (firstChar < '0' || firstChar > '9' || !isAsciiAlphaNum(subTag)) ? $assertionsDisabled : true;
            }
        } else if (isAsciiAlphaNum(subTag)) {
            return true;
        }
    }

    public final String getDisplayName() {
        return getDisplayName(getDefault(Category.DISPLAY));
    }

    public String getDisplayName(Locale locale) {
        String displayLanguage;
        String displayScript;
        String displayCountry;
        int count = 0;
        StringBuilder buffer = new StringBuilder();
        String languageCode = this.baseLocale.getLanguage();
        if (!languageCode.isEmpty()) {
            displayLanguage = getDisplayLanguage(locale);
            buffer.append(displayLanguage.isEmpty() ? languageCode : displayLanguage);
            count = 0 + 1;
        }
        displayLanguage = this.baseLocale.getScript();
        if (!displayLanguage.isEmpty()) {
            if (count == 1) {
                buffer.append(" (");
            }
            displayScript = getDisplayScript(locale);
            buffer.append(displayScript.isEmpty() ? displayLanguage : displayScript);
            count++;
        }
        displayScript = this.baseLocale.getRegion();
        if (!displayScript.isEmpty()) {
            if (count == 1) {
                buffer.append(" (");
            } else if (count == 2) {
                buffer.append(",");
            }
            displayCountry = getDisplayCountry(locale);
            buffer.append(displayCountry.isEmpty() ? displayScript : displayCountry);
            count++;
        }
        displayCountry = this.baseLocale.getVariant();
        if (!displayCountry.isEmpty()) {
            if (count == 1) {
                buffer.append(" (");
            } else if (count == 2 || count == 3) {
                buffer.append(",");
            }
            String displayVariant = getDisplayVariant(locale);
            buffer.append(displayVariant.isEmpty() ? displayCountry : displayVariant);
            count++;
        }
        if (count > 1) {
            buffer.append(")");
        }
        return buffer.toString();
    }

    public Object clone() {
        try {
            return (Locale) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    public int hashCode() {
        int hc = this.hashCodeValue;
        if (hc == 0) {
            hc = this.baseLocale.hashCode();
            if (this.localeExtensions != null) {
                hc ^= this.localeExtensions.hashCode();
            }
            this.hashCodeValue = hc;
        }
        return hc;
    }

    public boolean equals(Object obj) {
        boolean z = true;
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Locale)) {
            return $assertionsDisabled;
        }
        if (!this.baseLocale.equals(((Locale) obj).baseLocale)) {
            return $assertionsDisabled;
        }
        if (this.localeExtensions != null) {
            return this.localeExtensions.equals(((Locale) obj).localeExtensions);
        }
        if (((Locale) obj).localeExtensions != null) {
            z = $assertionsDisabled;
        }
        return z;
    }

    private static String formatList(String[] stringList, String listPattern, String listCompositionPattern) {
        int i = 0;
        if (listPattern == null || listCompositionPattern == null) {
            StringBuilder result = new StringBuilder();
            while (i < stringList.length) {
                if (i > 0) {
                    result.append(',');
                }
                result.append(stringList[i]);
                i++;
            }
            return result.toString();
        }
        Object stringList2;
        if (stringList2.length > 3) {
            stringList2 = composeList(new MessageFormat(listCompositionPattern), stringList2);
        }
        Object args = new Object[(stringList2.length + 1)];
        System.arraycopy(stringList2, 0, args, 1, stringList2.length);
        args[0] = new Integer(stringList2.length);
        return new MessageFormat(listPattern).format(args);
    }

    private static String[] composeList(MessageFormat format, String[] list) {
        if (list.length <= 3) {
            return list;
        }
        String newItem = format.format(new String[]{list[0], list[1]});
        Object newList = new String[(list.length - 1)];
        System.arraycopy((Object) list, 2, newList, 1, newList.length - 1);
        newList[0] = newItem;
        return composeList(format, newList);
    }

    private static boolean isUnicodeExtensionKey(String s) {
        return (s.length() == 2 && LocaleUtils.isAlphaNumericString(s)) ? true : $assertionsDisabled;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        PutField fields = out.putFields();
        fields.put("language", this.baseLocale.getLanguage());
        fields.put("script", this.baseLocale.getScript());
        fields.put("country", this.baseLocale.getRegion());
        fields.put("variant", this.baseLocale.getVariant());
        fields.put("extensions", this.localeExtensions == null ? "" : this.localeExtensions.getID());
        fields.put("hashcode", -1);
        out.writeFields();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        GetField fields = in.readFields();
        String script = (String) fields.get("script", (Object) "");
        String country = (String) fields.get("country", (Object) "");
        String variant = (String) fields.get("variant", (Object) "");
        String extStr = (String) fields.get("extensions", (Object) "");
        this.baseLocale = BaseLocale.getInstance(convertOldISOCodes((String) fields.get("language", (Object) "")), script, country, variant);
        if (extStr == null || extStr.length() <= 0) {
            this.localeExtensions = null;
            return;
        }
        try {
            InternalLocaleBuilder bldr = new InternalLocaleBuilder();
            bldr.setExtensions(extStr);
            this.localeExtensions = bldr.getLocaleExtensions();
        } catch (LocaleSyntaxException e) {
            throw new IllformedLocaleException(e.getMessage());
        }
    }

    private Object readResolve() throws ObjectStreamException {
        return getInstance(this.baseLocale.getLanguage(), this.baseLocale.getScript(), this.baseLocale.getRegion(), this.baseLocale.getVariant(), this.localeExtensions);
    }

    private static String convertOldISOCodes(String language) {
        language = LocaleUtils.toLowerString(language).intern();
        if (language == "he") {
            return "iw";
        }
        if (language == "yi") {
            return "ji";
        }
        if (language == PolicyInformation.ID) {
            return "in";
        }
        return language;
    }

    private static LocaleExtensions getCompatibilityExtensions(String language, String script, String country, String variant) {
        if (LocaleUtils.caseIgnoreMatch(language, "ja") && script.length() == 0 && LocaleUtils.caseIgnoreMatch(country, "jp") && "JP".equals(variant)) {
            return LocaleExtensions.CALENDAR_JAPANESE;
        }
        if (LocaleUtils.caseIgnoreMatch(language, "th") && script.length() == 0 && LocaleUtils.caseIgnoreMatch(country, "th") && "TH".equals(variant)) {
            return LocaleExtensions.NUMBER_THAI;
        }
        return null;
    }

    public static String adjustLanguageCode(String languageCode) {
        String adjusted = languageCode.toLowerCase(US);
        if (languageCode.equals("he")) {
            return "iw";
        }
        if (languageCode.equals(PolicyInformation.ID)) {
            return "in";
        }
        if (languageCode.equals("yi")) {
            return "ji";
        }
        return adjusted;
    }

    public static List<Locale> filter(List<LanguageRange> priorityList, Collection<Locale> locales, FilteringMode mode) {
        return LocaleMatcher.filter(priorityList, locales, mode);
    }

    public static List<Locale> filter(List<LanguageRange> priorityList, Collection<Locale> locales) {
        return filter(priorityList, locales, FilteringMode.AUTOSELECT_FILTERING);
    }

    public static List<String> filterTags(List<LanguageRange> priorityList, Collection<String> tags, FilteringMode mode) {
        return LocaleMatcher.filterTags(priorityList, tags, mode);
    }

    public static List<String> filterTags(List<LanguageRange> priorityList, Collection<String> tags) {
        return filterTags(priorityList, tags, FilteringMode.AUTOSELECT_FILTERING);
    }

    public static Locale lookup(List<LanguageRange> priorityList, Collection<Locale> locales) {
        return LocaleMatcher.lookup(priorityList, locales);
    }

    public static String lookupTag(List<LanguageRange> priorityList, Collection<String> tags) {
        return LocaleMatcher.lookupTag(priorityList, tags);
    }
}
