package net.sf.jabref.logic.integrity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.sf.jabref.BibDatabaseContext;
import net.sf.jabref.FileDirectoryPreferences;
import net.sf.jabref.logic.l10n.Localization;
import net.sf.jabref.logic.util.io.FileUtil;
import net.sf.jabref.model.entry.BibEntry;
import net.sf.jabref.model.entry.FieldName;
import net.sf.jabref.model.entry.FieldProperty;
import net.sf.jabref.model.entry.FileField;
import net.sf.jabref.model.entry.InternalBibtexFields;
import net.sf.jabref.model.entry.ParsedFileField;

import com.google.common.base.CharMatcher;

public class IntegrityCheck {

    private final BibDatabaseContext bibDatabaseContext;
    private final FileDirectoryPreferences fileDirectoryPreferences;

    public IntegrityCheck(BibDatabaseContext bibDatabaseContext, FileDirectoryPreferences fileDirectoryPreferences) {
        this.bibDatabaseContext = Objects.requireNonNull(bibDatabaseContext);
        this.fileDirectoryPreferences = Objects.requireNonNull(fileDirectoryPreferences);
    }

    public List<IntegrityMessage> checkBibtexDatabase() {
        List<IntegrityMessage> result = new ArrayList<>();

        for (BibEntry entry : bibDatabaseContext.getDatabase().getEntries()) {
            result.addAll(checkBibtexEntry(entry));
        }

        return result;
    }

    private List<IntegrityMessage> checkBibtexEntry(BibEntry entry) {
        List<IntegrityMessage> result = new ArrayList<>();

        if (entry == null) {
            return result;
        }

        result.addAll(new AuthorNameChecker().check(entry));

        // BibTeX only checkers
        if (!bibDatabaseContext.isBiblatexMode()) {
            result.addAll(new TitleChecker().check(entry));
            result.addAll(new PagesChecker().check(entry));
            result.addAll(new ASCIICharacterChecker().check(entry));
        } else {
            result.addAll(new BiblatexPagesChecker().check(entry));
        }

        result.addAll(new BracketChecker(FieldName.TITLE).check(entry));
        result.addAll(new YearChecker().check(entry));
        result.addAll(new UrlChecker().check(entry));
        result.addAll(new FileChecker(bibDatabaseContext, fileDirectoryPreferences).check(entry));
        result.addAll(new TypeChecker().check(entry));
        for (String journalField : InternalBibtexFields.getJournalNameFields()) {
            result.addAll(new AbbreviationChecker(journalField).check(entry));
        }
        for (String bookNameField : InternalBibtexFields.getBookNameFields()) {
            result.addAll(new AbbreviationChecker(bookNameField).check(entry));
        }
        result.addAll(new BibStringChecker().check(entry));
        result.addAll(new HTMLCharacterChecker().check(entry));
        result.addAll(new BooktitleChecker().check(entry));
        result.addAll(new ISSNChecker().check(entry));
        result.addAll(new ISBNChecker().check(entry));

        return result;
    }


    @FunctionalInterface
    public interface Checker {
        List<IntegrityMessage> check(BibEntry entry);
    }

    private static class TypeChecker implements Checker {

        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            Optional<String> value = entry.getFieldOptional(FieldName.PAGES);
            if (!value.isPresent()) {
                return Collections.emptyList();
            }

            if ("proceedings".equalsIgnoreCase(entry.getType())) {
                return Collections.singletonList(new IntegrityMessage(Localization.lang("wrong entry type as proceedings has page numbers"), entry, FieldName.PAGES));
            }

            return Collections.emptyList();
        }
    }

    private static class BooktitleChecker implements Checker {

        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            String field = FieldName.BOOKTITLE;
            Optional<String> value = entry.getFieldOptional(field);
            if (!value.isPresent()) {
                return Collections.emptyList();
            }

            if (value.get().toLowerCase(Locale.ENGLISH).endsWith("conference on")) {
                return Collections.singletonList(new IntegrityMessage(Localization.lang("booktitle ends with 'conference on'"), entry, field));
            }

            return Collections.emptyList();
        }
    }

    private static class AbbreviationChecker implements Checker {

        private final String field;

        private AbbreviationChecker(String field) {
            this.field = field;
        }

        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            Optional<String> value = entry.getFieldOptional(field);
            if (!value.isPresent()) {
                return Collections.emptyList();
            }

            if (value.get().contains(".")) {
                return Collections.singletonList(new IntegrityMessage(Localization.lang("abbreviation detected"), entry, field));
            }

            return Collections.emptyList();
        }
    }

    private static class FileChecker implements Checker {

        private final BibDatabaseContext context;
        private final FileDirectoryPreferences fileDirectoryPreferences;

        private FileChecker(BibDatabaseContext context, FileDirectoryPreferences fileDirectoryPreferences) {
            this.context = context;
            this.fileDirectoryPreferences = fileDirectoryPreferences;
        }

        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            Optional<String> value = entry.getFieldOptional(FieldName.FILE);
            if (!value.isPresent()) {
                return Collections.emptyList();
            }

            List<ParsedFileField> parsedFileFields = FileField.parse(value.get()).stream()
                    .filter(p -> !(p.getLink().startsWith("http://") || p.getLink().startsWith("https://")))
                    .collect(Collectors.toList());

            for (ParsedFileField p : parsedFileFields) {
                Optional<File> file = FileUtil.expandFilename(context, p.getLink(), fileDirectoryPreferences);
                if ((!file.isPresent()) || !file.get().exists()) {
                    return Collections.singletonList(
                            new IntegrityMessage(Localization.lang("link should refer to a correct file path"), entry,
                                    FieldName.FILE));
                }
            }

            return Collections.emptyList();
        }
    }

    private static class UrlChecker implements Checker {

        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            Optional<String> value = entry.getFieldOptional(FieldName.URL);
            if (!value.isPresent()) {
                return Collections.emptyList();
            }

            if (!value.get().contains("://")) {
                return Collections.singletonList(new IntegrityMessage(Localization.lang("should contain a protocol") + ": http[s]://, file://, ftp://, ...", entry, FieldName.URL));
            }

            return Collections.emptyList();
        }
    }

    private static class AuthorNameChecker implements Checker {

        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            List<IntegrityMessage> result = new ArrayList<>();
            for (String field : entry.getFieldNames()) {
                if (InternalBibtexFields.getFieldProperties(field).contains(FieldProperty.PERSON_NAMES)) {
                    Optional<String> value = entry.getFieldOptional(field);
                    if (!value.isPresent()) {
                        return Collections.emptyList();
                    }

                    String valueTrimmedAndLowerCase = value.get().trim().toLowerCase();
                    if (valueTrimmedAndLowerCase.startsWith("and ") || valueTrimmedAndLowerCase.startsWith(",")) {
                        result.add(new IntegrityMessage(Localization.lang("should start with a name"), entry, field));
                    } else if (valueTrimmedAndLowerCase.endsWith(" and") || valueTrimmedAndLowerCase.endsWith(",")) {
                        result.add(new IntegrityMessage(Localization.lang("should end with a name"), entry, field));
                    }
                }
            }
            return result;
        }
    }

    private static class BracketChecker implements Checker {

        private final String field;

        private BracketChecker(String field) {
            this.field = field;
        }

        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            Optional<String> value = entry.getFieldOptional(field);
            if (!value.isPresent()) {
                return Collections.emptyList();
            }

            // metaphor: integer-based stack (push + / pop -)
            int counter = 0;
            for (char a : value.get().trim().toCharArray()) {
                if (a == '{') {
                    counter++;
                } else if (a == '}') {
                    if (counter == 0) {
                        return Collections.singletonList(new IntegrityMessage(Localization.lang("unexpected closing curly bracket"), entry, field));
                    } else {
                        counter--;
                    }
                }
            }

            if (counter > 0) {
                return Collections.singletonList(new IntegrityMessage(Localization.lang("unexpected opening curly bracket"), entry, field));
            }

            return Collections.emptyList();
        }

    }

    private static class TitleChecker implements Checker {

        private static final Pattern INSIDE_CURLY_BRAKETS = Pattern.compile("\\{[^}\\{]*\\}");
        private static final Predicate<String> HAS_CAPITAL_LETTERS = Pattern.compile("[\\p{Lu}\\p{Lt}]").asPredicate();

        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            Optional<String> value = entry.getFieldOptional(FieldName.TITLE);
            if (!value.isPresent()) {
                return Collections.emptyList();
            }


            /*
             * Algorithm:
             * - remove trailing whitespaces
             * - ignore first letter as this can always be written in caps
             * - remove everything that is in brackets
             * - check if at least one capital letter is in the title
             */
            String valueTrimmed = value.get().trim();
            String valueIgnoringFirstLetter = valueTrimmed.startsWith("{") ? valueTrimmed : valueTrimmed.substring(1);
            String valueOnlySpacesWithinCurlyBraces = valueIgnoringFirstLetter;
            while (true) {
                Matcher matcher = INSIDE_CURLY_BRAKETS.matcher(valueOnlySpacesWithinCurlyBraces);
                if (!matcher.find()) {
                    break;
                }
                valueOnlySpacesWithinCurlyBraces = matcher.replaceAll("");
            }

            boolean hasCapitalLettersThatBibtexWillConvertToSmallerOnes = HAS_CAPITAL_LETTERS.test(valueOnlySpacesWithinCurlyBraces);

            if (hasCapitalLettersThatBibtexWillConvertToSmallerOnes) {
                return Collections.singletonList(new IntegrityMessage(Localization.lang("large capitals are not masked using curly brackets {}"), entry, FieldName.TITLE));
            }

            return Collections.emptyList();
        }
    }

    private static class YearChecker implements Checker {

        private static final Predicate<String> CONTAINS_FOUR_DIGIT = Pattern.compile("([^0-9]|^)[0-9]{4}([^0-9]|$)").asPredicate();

        /**
         * Checks, if the number String contains a four digit year
         */
        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            Optional<String> value = entry.getFieldOptional(FieldName.YEAR);
            if (!value.isPresent()) {
                return Collections.emptyList();
            }

            if (!CONTAINS_FOUR_DIGIT.test(value.get().trim())) {
                return Collections.singletonList(new IntegrityMessage(Localization.lang("should contain a four digit number"), entry, FieldName.YEAR));
            }

            return Collections.emptyList();
        }
    }

    /**
     * From BibTex manual:
     * One or more page numbers or range of numbers, such as 42--111 or 7,41,73--97 or 43+
     * (the '+' in this last example indicates pages following that don't form a simple range).
     * To make it easier to maintain Scribe-compatible databases, the standard styles convert
     * a single dash (as in 7-33) to the double dash used in TEX to denote number ranges (as in 7--33).
     */
    private static class PagesChecker implements Checker {

        private static final String PAGES_EXP = ""
                + "\\A"                       // begin String
                + "\\d+"                      // number
                + "(?:"                       // non-capture group
                + "\\+|\\-{2}\\d+"            // + or --number (range)
                + ")?"                        // optional group
                + "(?:"                       // non-capture group
                + ","                         // comma
                + "\\d+(?:\\+|\\-{2}\\d+)?"   // repeat former pattern
                + ")*"                        // repeat group 0,*
                + "\\z";                      // end String

        private static final Predicate<String> VALID_PAGE_NUMBER = Pattern.compile(PAGES_EXP).asPredicate();

        /**
         * Checks, if the page numbers String conforms to the BibTex manual
         */
        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            Optional<String> value = entry.getFieldOptional(FieldName.PAGES);
            if (!value.isPresent()) {
                return Collections.emptyList();
            }

            if (!VALID_PAGE_NUMBER.test(value.get().trim())) {
                return Collections.singletonList(new IntegrityMessage(Localization.lang("should contain a valid page number range"), entry, FieldName.PAGES));
            }

            return Collections.emptyList();
        }
    }

    /**
     * Same as {@link PagesChecker} but allows single dash as well
     */
    private static class BiblatexPagesChecker implements Checker {

        private static final String PAGES_EXP = ""
                + "\\A"                       // begin String
                + "\\d+"                      // number
                + "(?:"                       // non-capture group
                + "\\+|\\-{1,2}\\d+"            // + or --number (range)
                + ")?"                        // optional group
                + "(?:"                       // non-capture group
                + ","                         // comma
                + "\\d+(?:\\+|\\-{1,2}\\d+)?"   // repeat former pattern
                + ")*"                        // repeat group 0,*
                + "\\z";                      // end String

        private static final Predicate<String> VALID_PAGE_NUMBER = Pattern.compile(PAGES_EXP).asPredicate();

        /**
         * Checks, if the page numbers String conforms to the BibTex manual
         */
        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            Optional<String> value = entry.getFieldOptional(FieldName.PAGES);
            if (!value.isPresent()) {
                return Collections.emptyList();
            }

            if (!VALID_PAGE_NUMBER.test(value.get().trim())) {
                return Collections.singletonList(new IntegrityMessage(Localization.lang("should contain a valid page number range"), entry, FieldName.PAGES));
            }

            return Collections.emptyList();
        }
    }

    private static class BibStringChecker implements Checker {

        // Detect # if it doesn't have a \ in front of it or if it starts the string
        private static final Pattern UNESCAPED_HASH = Pattern.compile("(?<!\\\\)#|^#");

        /**
         * Checks, if there is an even number of unescaped #
         */
        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            List<IntegrityMessage> results = new ArrayList<>();

            Map<String, String> fields = entry.getFieldMap();


            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (!InternalBibtexFields.getFieldProperties(field.getKey()).contains(FieldProperty.VERBATIM)) {
                    Matcher hashMatcher = UNESCAPED_HASH.matcher(field.getValue());
                    int hashCount = 0;
                    while (hashMatcher.find()) {
                        hashCount++;
                    }
                    if ((hashCount & 1) == 1) { // Check if odd
                        results.add(new IntegrityMessage(Localization.lang("odd number of unescaped '#'"), entry,
                                field.getKey()));
                    }
                }
            }
            return results;
        }
    }

    private static class HTMLCharacterChecker implements Checker {

        // Detect any HTML encoded character,
        private static final Pattern HTML_CHARACTER_PATTERN = Pattern.compile("&[#\\p{Alnum}]+;");

        /**
         * Checks, if there are any HTML encoded characters in the fields
         */
        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            List<IntegrityMessage> results = new ArrayList<>();
            for (Map.Entry<String, String> field : entry.getFieldMap().entrySet()) {
                Matcher characterMatcher = HTML_CHARACTER_PATTERN.matcher(field.getValue());
                if (characterMatcher.find()) {
                    results.add(new IntegrityMessage(Localization.lang("HTML encoded character found"), entry,
                            field.getKey()));
                }
            }
            return results;
        }
    }

    private static class ASCIICharacterChecker implements Checker {
        /**
         * Detect any non ASCII encoded characters, e.g., umlauts or unicode in the fields
         */
        @Override
        public List<IntegrityMessage> check(BibEntry entry) {
            List<IntegrityMessage> results = new ArrayList<>();
            for (Map.Entry<String, String> field : entry.getFieldMap().entrySet()) {
                boolean asciiOnly = CharMatcher.ascii().matchesAllOf(field.getValue());
                if (!asciiOnly) {
                    results.add(new IntegrityMessage(Localization.lang("Non-ASCII encoded character found"), entry,
                            field.getKey()));
                }
            }
            return results;
        }
    }

}
