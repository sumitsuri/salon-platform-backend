package com.salonplatform.aigateway;

import com.salonplatform.aigateway.inspect.SensitiveDataScanner;
import com.salonplatform.aigateway.inspect.Validators;
import com.salonplatform.aigateway.model.Category;
import com.salonplatform.aigateway.model.Finding;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataScannerTest {

    private final SensitiveDataScanner scanner = new SensitiveDataScanner();

    private List<Category> categories(String text) {
        return scanner.scan(text).stream().map(Finding::category).toList();
    }

    /** A 12-digit number that passes the Verhoeff check, built rather than hard-coded. */
    private static String validAadhaar() {
        String base = "23456789012";
        for (int d = 0; d < 10; d++) {
            if (Validators.verhoeff(base + d)) return base + d;
        }
        throw new IllegalStateException("no valid check digit");
    }

    // ---- credentials ----

    @Test
    void apiKeysAndTokensOfCommonProvidersAreCaught() {
        assertThat(categories("key sk-ant-api03-" + "A".repeat(40))).containsExactly(Category.CREDENTIAL);
        assertThat(categories("use sk-" + "b".repeat(32))).containsExactly(Category.CREDENTIAL);
        assertThat(categories("AKIA" + "ABCDEFGHIJKLMNOP")).containsExactly(Category.CREDENTIAL);
        assertThat(categories("AIza" + "x".repeat(35))).containsExactly(Category.CREDENTIAL);
        assertThat(categories("ghp_" + "a1".repeat(18))).containsExactly(Category.CREDENTIAL);
        assertThat(categories("xoxb-1234567890-abcdefghij")).containsExactly(Category.CREDENTIAL);
        assertThat(categories("-----BEGIN RSA PRIVATE KEY-----")).containsExactly(Category.CREDENTIAL);
    }

    @Test
    void jwtsBearerTokensAndConnectionStringsAreCaught() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijk12345";
        assertThat(categories("token " + jwt)).containsExactly(Category.CREDENTIAL);
        assertThat(categories("Authorization: Bearer abcdef0123456789abcdef0123456789")).containsExactly(Category.CREDENTIAL);
        assertThat(categories("postgresql://salon:Sup3rSecret@db.internal:5432/salon")).containsExactly(Category.CREDENTIAL);
    }

    @Test
    void passwordsGivenInASentenceAreCaught() {
        assertThat(categories("my password is Hunter2!")).containsExactly(Category.CREDENTIAL);
        assertThat(categories("login: admin password: Abc12345")).containsExactly(Category.CREDENTIAL);
        assertThat(categories("the api key = 9f8e7d6c5b4a")).containsExactly(Category.CREDENTIAL);
        assertThat(categories("otp is 482913")).containsExactly(Category.CREDENTIAL);
    }

    @Test
    void talkingAboutPasswordsWithoutGivingOneIsNotFlagged() {
        assertThat(categories("How do I reset a staff password?")).isEmpty();
        assertThat(categories("password is incorrect")).isEmpty();
        assertThat(categories("our password policy is strict")).isEmpty();
        assertThat(categories("secret is out")).isEmpty();
    }

    // ---- payment and identity ----

    @Test
    void realCardNumbersAreCaughtButOrdinaryLongNumbersAreNot() {
        assertThat(categories("card 4111 1111 1111 1111")).containsExactly(Category.PAYMENT_CARD);
        assertThat(categories("card 4111-1111-1111-1111 cvv 123")).contains(Category.PAYMENT_CARD);
        assertThat(categories("card 4111 1111 1111 1112")).isEmpty(); // fails the Luhn check
        assertThat(categories("revenue was 1082245 over 567 visits")).isEmpty();
        assertThat(categories("order 20261123123456789")).isEmpty();
    }

    @Test
    void aadhaarNeedsAValidChecksum() {
        String a = validAadhaar();
        assertThat(categories("aadhaar " + a)).containsExactly(Category.GOVERNMENT_ID);
        String spaced = a.substring(0, 4) + " " + a.substring(4, 8) + " " + a.substring(8);
        assertThat(categories("aadhaar " + spaced)).containsExactly(Category.GOVERNMENT_ID);
        char last = a.charAt(11);
        String wrong = a.substring(0, 11) + (char) ('0' + (last - '0' + 1) % 10);
        assertThat(categories("number " + wrong)).isEmpty();
    }

    @Test
    void panAndBankAccountsAreCaught() {
        assertThat(categories("PAN is ABCDE1234F")).containsExactly(Category.GOVERNMENT_ID);
        assertThat(categories("account number 123456789012")).containsExactly(Category.BANK_ACCOUNT);
        assertThat(categories("a/c: 00112233445566")).containsExactly(Category.BANK_ACCOUNT);
    }

    @Test
    void phoneNumbersAndEmailsAreCaught() {
        assertThat(categories("call 9845012345")).containsExactly(Category.PHONE);
        assertThat(categories("call +91 9845012345")).containsExactly(Category.PHONE);
        assertThat(categories("mail priya.sharma@example.com")).containsExactly(Category.EMAIL);
        assertThat(categories("sales were 98450 in total")).isEmpty();
    }

    // ---- behaviour ----

    @Test
    void digitRunsInsideIdentifiersAreNotMistakenForPhoneNumbersOrCards() {
        String id = "123e4567-e89b-12d3-a456-c9876543210f"; // c + ten digits would look like a phone number
        assertThat(categories("{\"branchId\":\"" + id + "\",\"revenue\":1082245}")).isEmpty();
        assertThat(scanner.redact(id, scanner.scan(id), EnumSet.allOf(Category.class))).isEqualTo(id);
        assertThat(categories("call 9845012345 about " + id)).containsExactly(Category.PHONE);
    }

    @Test
    void aCredentialIsStillFoundEvenInsideAnIdentifierShapedValue() {
        assertThat(categories("password: 123e4567-e89b-12d3-a456-426614174000")).contains(Category.CREDENTIAL);
    }

    @Test
    void findingsNeverCarryTheMatchedText() {
        String secret = "sk-ant-api03-" + "Z".repeat(40);
        List<Finding> findings = scanner.scan("here " + secret);
        assertThat(findings.toString()).doesNotContain(secret).doesNotContain("ZZZZ");
    }

    @Test
    void redactionReplacesOnlyTheChosenCategories() {
        String text = "call 9845012345 or mail a@b.co, key sk-ant-api03-" + "Q".repeat(30);
        List<Finding> f = scanner.scan(text);
        String out = scanner.redact(text, f, Set.of(Category.PHONE, Category.EMAIL));
        assertThat(out).contains("[REDACTED:PHONE]").contains("[REDACTED:EMAIL]").contains("sk-ant-api03-").doesNotContain("9845012345");
        String all = scanner.redact(text, f, EnumSet.allOf(Category.class));
        assertThat(all).doesNotContain("QQQQ");
    }

    @Test
    void overlappingRulesReportTheSecretOnce() {
        assertThat(scanner.scan("sk-ant-api03-" + "A".repeat(40))).hasSize(1);
    }

    @Test
    void emptyAndNullTextAreFine() {
        assertThat(scanner.scan(null)).isEmpty();
        assertThat(scanner.scan("")).isEmpty();
    }
}
