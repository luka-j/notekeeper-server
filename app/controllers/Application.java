package controllers;

import models.GroupMember;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.faq;
import views.html.index;
import views.html.login;

import java.io.File;

public class Application extends Controller {

    public static final int ITERATON = 13;

    public Result index(String lang) {
        if(lang != null)
            changeLang(lang);
        else
            lang=lang().code();
        return ok(index.render(lang));
    }

    public Result faq() {
        return ok(faq.render());
    }

    public Result showEula() {
        response().setHeader("Content-Disposition", "inline");
        return ok(new File("/home/luka/legal/eula")).as("text/plain;charset=utf-8");
    }

    public Result showPrivacyPolicy() {
        response().setHeader("Content-Disposition", "inline");
        return ok(new File("/home/luka/legal/privacy")).as("text/plain;charset=utf-8");
    }

    public Result loginPage() {
        return ok(login.render());
    }

    public Result flushLogs() {
        return Restrict.ROOT.require(ctx(), 1, (GroupMember member) -> {
            Logging.flushWriters();
            return ok("flushed");
        });
    }

    public Result getFavicon() {
        return redirect("/assets/images/favicon.ico");
    }

    /*public Result testEmail() {
        Emails.testEmail();
        return ok("sent");
    }*/
    public Result getIterationCode() {
        return ok(String.valueOf(ITERATON));
    }
}
