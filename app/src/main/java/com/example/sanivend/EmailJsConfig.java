package com.example.sanivend;

/**
 * EmailJS account details for sending the 6-digit verification code (free plan:
 * 200 emails a month, no server needed). Fill these in from https://dashboard.emailjs.com:
 *
 *   SERVICE_ID   Email Services → your Gmail service, e.g. "service_ab12cd3"
 *   TEMPLATE_ID  Email Templates → the "SaniVend code" template, e.g. "template_x9y8z7"
 *   PUBLIC_KEY   Account → General → Public Key
 *   PRIVATE_KEY  Account → General → Private Key (recommended; leave "" to skip)
 *
 * Also turn ON: Account → Security → "Allow EmailJS API for non-browser applications",
 * otherwise EmailJS refuses requests from the Android app.
 *
 * The template must use these variables: {{to_email}} (in "To Email"), {{code}},
 * {{minutes}} and {{app_name}}. See the SaniVend guide for a ready-made template.
 */
public final class EmailJsConfig {

    public static final String SERVICE_ID = "YOUR_SERVICE_ID";
    public static final String TEMPLATE_ID = "YOUR_TEMPLATE_ID";
    public static final String PUBLIC_KEY = "YOUR_PUBLIC_KEY";
    public static final String PRIVATE_KEY = "";

    static final String SEND_URL = "https://api.emailjs.com/api/v1.0/email/send";

    private EmailJsConfig() {}

    /** False until the placeholders above are replaced. */
    public static boolean isSetUp() {
        return !SERVICE_ID.startsWith("YOUR_") && !TEMPLATE_ID.startsWith("YOUR_")
                && !PUBLIC_KEY.startsWith("YOUR_");
    }
}
