package mails;

import org.json.JSONException;

/**
 * Created by luka on 11.2.16..
 */
public class Emails {

    public static SendGrid.Response invite(String invitersName, String groupName, String emailAddr) throws SendGridException, JSONException {
        SendGrid platform = new SendGrid("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");
        SendGrid.Email email = new SendGrid.Email();
        email.setFrom("invite@studybuddy.me");
        email.setFromName("StudyBuddy");
        email.addTo(emailAddr);
        email.setTemplateId("6d053625-02c2-4cdb-b509-c31154919d3d");
        email.addSubstitution("name", new String[]{invitersName});
        email.addSubstitution("group", new String[]{groupName});
        return platform.send(email);
    }
}
