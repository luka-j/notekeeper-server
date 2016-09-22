package controllers;

import models.*;
import org.mindrot.jbcrypt.BCrypt;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static controllers.Restrict.READ;
import static controllers.Restrict.READ_PRESERVE_GROUP;

/**
 * Created by luka on 13.12.15..
 */
public class Users extends Controller {
    Form<User> userForm = Form.form(User.class);

    public Result register() {
        Form<User> filledForm = userForm.bindFromRequest();
        if(filledForm.hasErrors()) {
            return badRequest("Bad request");
        } else {
            try {
                User user = filledForm.get();
                String password = User.register(user);
                String token = User.login(user.email, password);
                Invitation inv = Invitation.get(user.email);
                if(inv != null) {
                    for(Long id : inv.getGroupIds()) {
                        Group.invite(id, user, inv.approved);
                    }
                    inv.delete();
                }
                if (token != null)
                    return created(token);
                else
                    return unauthorized("Cannot login after registration. Oops (my bad)!");
            } catch (DuplicateException ex) {
                return ex.getResponse();
            }
        }
    }

    public Result login() {
        DynamicForm form = Form.form().bindFromRequest();
        String token = User.login(form.get("email"), form.get("pass"));
        if(token != null)
            return ok(token);
        else
            return unauthorized();
    }

    public Result refreshToken(String oldToken) {
        String newToken;
        try {
            newToken = User.get(User.verifyTokenPayload(oldToken).get(User.JWT_SUBJECT).asLong()).generateToken();
            return ok(newToken);
        } catch (TokenException e) {
            return User.handleTokenException(e);
        }
    }

    public Result getUser(long id) {
        return ok(User.get(id).toString());
    }

    public Result joinGroup(long groupId) {
        Restrict access = READ;
        return access.require(ctx(), -1, (GroupMember member) -> {
            Group.requestWrite(member.user, groupId);
            access.log(member, "Users/requestWrite, id: " + groupId);
            return ok("Sent request to " + groupId);
        });
    }

    public Result leaveGroup(long groupId) {
        Restrict access = READ_PRESERVE_GROUP;
        return access.require(ctx(), groupId, (GroupMember member) -> {
            member.delete();
            access.log(member, "Users/leaveGroup");
            return ok("Left");
        });
    }

    public Result getGroups() {
        return READ.require(ctx(), -1, (GroupMember member) ->
                ok(Json.toJson(member.user.groups.stream().filter((GroupMember g) -> g.permission >= GroupMember.PERM_REQUEST_WRITE)
                .collect(Collectors.toList()))));
    }

    public Result getMyDetails() {
        return READ.require(ctx(), -1, (GroupMember member) -> {
            User user = member.user;
            return ok(Json.newObject().put("id", user.id)
                    .put("username", user.username)
                    .put("email", user.email)
                    .put("hasImage", user.hasImage)
                    .toString());
        });
    }

    public Result getImage(long id, int size) {
        return READ.require(ctx(), -1, (GroupMember member) ->{
            File img;
            try {
                img = User.getImage(id, size);
                if(img!=null) {
                    response().setHeader("Content-Disposition", "inline");
                    return ok(img);
                } else
                    return ok();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return internalServerError();
            }
        });
    }

    public Result setMyImage() {
        return READ.require(ctx(), -1, (GroupMember member) -> {
            File img = request().body().asRaw().asFile();
            try {
                User.updateImage(member.user, img);
                return created("Done");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return internalServerError("500");
            }
        });
    }

    public Result setMyProfile() {
        return READ.require(ctx(), -1, (GroupMember member) -> {
            Map<String, String[]> form = request().body().asFormUrlEncoded();
            if(!form.containsKey("username") || !form.containsKey("email")) return badRequest("Bad request");
            String username, email;
            username = form.get("username")[0];
            email = form.get("email")[0];
            member.user.username = username;
            member.user.email = email;
            member.user.update();
            return ok("Done");
        });
    }

    public Result checkPassword(String password) {
        return READ.require(ctx(), -1, (GroupMember member) -> {
            if(BCrypt.checkpw(password, member.user.password))
                return ok("ok");
            else
                return unauthorized("wrong password");
        });
    }

    public Result changePassword() {
        return READ.require(ctx(), -1, (GroupMember member) -> {
            Map<String, String[]> params = request().body().asFormUrlEncoded();
            String oldPass = params.get("old")[0], newPass = params.get("pwd")[0];
            if(BCrypt.checkpw(oldPass, member.user.password)) {
                member.user.changePassword(newPass);
                return ok("Changed pass");
            } else {
                return unauthorized("wrong password");
            }
        });
    }
}
