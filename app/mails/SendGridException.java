package mails;

/**
 * Created by luka on 11.2.16..
 */
public class SendGridException extends Exception {
    public SendGridException(Exception e) {
        super(e);
    }
}
