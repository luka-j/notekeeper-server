package mails;

import com.sendgrid.*;
import play.Play;

import java.io.IOException;

/**
 * Created by luka on 29.12.16..
 */
public class Emails {

    public static void invite(String address, String inviterName, String groupName) throws IOException {
        Email from = new Email("invite@notekeeper.cf", "Notekeeper invitation");
        String subject = "";
        Email to = new Email(address);
        Content content = new Content("text/html", " ");
        Mail mail = new Mail(from, subject, to, content);
        mail.personalization.get(0).addSubstitution("-inviter-", inviterName);
        mail.personalization.get(0).addSubstitution("-groupname-", groupName);
        mail.setTemplateId("6d053625-02c2-4cdb-b509-c31154919d3d");

        SendGrid sg = new SendGrid("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");
        sendMail(mail, sg);
    }

    /*public static void testEmail() {
        Email from = new Email("invite@notekeeper.cf", "Notekeeper invitation");
        String subject = "";
        Email to = new Email("luka.jovicic16@gmail.com");
        Content content = new Content("text/html", " ");
        Mail mail = new Mail(from, subject, to, content);
        mail.personalization.get(0).addSubstitution("-inviter-", "Luka (šizofrenično)");
        mail.personalization.get(0).addSubstitution("-groupname-", "mgtest");
        mail.setTemplateId("6d053625-02c2-4cdb-b509-c31154919d3d");

        SendGrid sg = new SendGrid("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");
        sendMail(mail, sg);
    }

    public static void initTestEmail() {
        Email from = new Email("test@notekeeper.cf");
        String subject = "Hello World from the SendGrid Java Library!";
        Email to = new Email("luka.jovicic16@gmail.com");
        Content content = new Content("text/plain", "Hello, Email!");
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid("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");
        sendMail(mail, sg);
    }*/

    private static void sendMail(Mail mail, SendGrid sg) throws IOException {
        Request request = new Request();
        try {
            request.method = Method.POST;
            request.endpoint = "mail/send";
            request.body = mail.build();
            Response response = sg.api(request);
            if(Play.isDev()) {
                System.out.println(response.statusCode);
                System.out.println(response.body);
                System.out.println(response.headers);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            throw ex;
        }
    }
}
