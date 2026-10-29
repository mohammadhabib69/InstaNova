package com.insta.nova.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.insta.nova.R;

/**
 * Community Guidelines, Privacy Policy and Terms of Service for InstaNova.
 * One activity, real short-form text selected by the intent extra.
 */
public class LegalActivity extends SettingsPageActivity {

    public static final String EXTRA_DOC = "extra_doc";
    public static final String DOC_GUIDELINES = "guidelines";
    public static final String DOC_PRIVACY = "privacy";
    public static final String DOC_TERMS = "terms";

    public static Intent docIntent(Context context, String doc) {
        return new Intent(context, LegalActivity.class).putExtra(EXTRA_DOC, doc);
    }

    public static Intent contactIntent(Context context) {
        return new Intent(context, ContactSupportActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String doc = getIntent().getStringExtra(EXTRA_DOC);
        if (doc == null) doc = DOC_GUIDELINES;

        if (DOC_PRIVACY.equals(doc)) {
            setPageTitle(R.string.st_privacy_policy);
            privacy();
        } else if (DOC_TERMS.equals(doc)) {
            setPageTitle(R.string.st_terms);
            terms();
        } else {
            setPageTitle(R.string.st_community_guidelines);
            guidelines();
        }
    }

    private void guidelines() {
        addParagraph("InstaNova is a place to share photos, videos and messages with people you choose. "
                + "These guidelines explain what is welcome here and what is not.");
        addSection("Be yourself, be kind");
        addParagraph("Treat people the way you want to be treated. Harassment, bullying, threats and "
                + "coordinated pile-ons are not allowed. Disagree with ideas; don't attack people.");
        addSection("Respect privacy");
        addParagraph("Don't post someone else's private information — home address, phone number, ID "
                + "documents or intimate images — without their permission. If someone asks you to remove a "
                + "photo of them, do it.");
        addSection("No hate or violence");
        addParagraph("Content that promotes violence, praises violent extremism, or attacks people based on "
                + "race, ethnicity, religion, disability, gender or sexual orientation is removed.");
        addSection("Authentic accounts");
        addParagraph("Don't impersonate other people, create fake accounts, or use automation to inflate "
                + "followers, likes or comments. Spam and bulk unsolicited messages are removed.");
        addSection("Content you own");
        addParagraph("Post work you created or have permission to use. Respect copyright and trademarks.");
        addSection("Reporting");
        addParagraph("If you see something that breaks these rules, use Report a problem in Settings or the "
                + "three-dot menu on the content. Reports are private.");
    }

    private void privacy() {
        addParagraph("This policy explains what InstaNova collects, why, and the choices you have. "
                + "InstaNova is operated by Velkyn.");
        addSection("What we collect");
        addParagraph("Account details you provide: email address, name, username, password (stored by our "
                + "authentication provider, never in plain text by us), bio and profile photo. Content you "
                + "create: posts, stories, comments, messages and calls. Usage data needed to run the product, "
                + "such as which screens you open and basic device information.");
        addSection("How we use it");
        addParagraph("To sign you in, show your feed, deliver messages and calls, keep the service secure, "
                + "and understand which features need work. We do not sell your personal information.");
        addSection("Who can see your content");
        addParagraph("Public accounts are visible to everyone. Private accounts are visible to approved "
                + "followers. Your story audience settings and blocked/restricted lists are respected. "
                + "Messages are visible to the people in the conversation.");
        addSection("Storage and security");
        addParagraph("Data is stored with our backend providers. Access is protected by row-level rules so "
                + "one account cannot read another account's private rows. No system is perfectly secure, but "
                + "we work to protect your information.");
        addSection("Your choices");
        addParagraph("You can edit your profile, change privacy settings, download a copy of your data, "
                + "deactivate your account, or delete it permanently from Settings. Deletion removes your "
                + "profile and the content that belongs to it.");
        addSection("Contact");
        addParagraph("Questions about privacy: " + getString(R.string.st_contact_email) + ".");
    }

    private void terms() {
        addParagraph("By using InstaNova you agree to these terms. If you do not agree, do not use the app.");
        addSection("Who can use InstaNova");
        addParagraph("You must be at least 13 years old and able to form a binding contract in your country. "
                + "You are responsible for activity on your account and for keeping your password secure.");
        addSection("Your content");
        addParagraph("You keep ownership of the content you post. You grant InstaNova a licence to host, "
                + "display and deliver that content to the people you share it with. You confirm you have the "
                + "rights to post it.");
        addSection("Acceptable use");
        addParagraph("Do not use InstaNova to break the law, harm others, distribute malware, scrape other "
                + "people's data, or interfere with the service. Accounts that do may be restricted or removed.");
        addSection("Service availability");
        addParagraph("We work to keep InstaNova available, but it is provided as is. Features may change or be "
                + "withdrawn, and maintenance or network conditions may interrupt access.");
        addSection("Ending the agreement");
        addParagraph("You can stop using InstaNova at any time and delete your account in Settings. We may "
                + "suspend accounts that seriously or repeatedly break these terms or the Community Guidelines.");
        addSection("Contact");
        addParagraph("Questions about these terms: " + getString(R.string.st_contact_email) + ".");
    }
}
