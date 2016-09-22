package controllers;

import models.Group;
import models.GroupMember;
import models.Invitation;
import models.User;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static controllers.Restrict.*;

/**
 * Created by luka on 11.12.15..
 */
public class Groups extends Controller {
    static Form<Group> groupForm = Form.form(Group.class);

    public Result getGroup(long groupId) {
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), groupId,
                (GroupMember member) -> ok(Json.toJson(Group.get(groupId, member.permission))).as("application/json"));
    }

    public Result updateGroup(long groupId) {
        Restrict access = MODIFY;
        return access.require(ctx(), groupId, (GroupMember member) -> {
            Form<Group> filledForm = groupForm.bindFromRequest();
            Group.update(groupId, filledForm.get());
            access.log(member, "Groups/edit");
            return ok("Done");
        });
    }

    public Result addImage(long id) {
        Restrict access = WRITE;
        return access.require(ctx(), id, (GroupMember member) -> {
            File img = request().body().asRaw().asFile();
            try {
                Group.updateImage(Group.get(id, member.permission), img);
                access.log(member, "Groups/addImage");
                return created("Done");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return internalServerError("500");
            }
        });
    }

    public Result getImage(long id, int size) {
        Restrict access = READ_PRESERVE_GROUP;
        return access.require(ctx(), id, (GroupMember member) -> {
            try {
                File img = Group.getImage(id, size);
                access.log(member, "Groups/getImage");
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

    public Result addGroup() {
        Form<Group> filledForm = groupForm.bindFromRequest();
        if(filledForm.hasErrors()) return badRequest("Bad request");
        Group group = filledForm.get();
        Restrict access = READ_PRESERVE_GROUP;
        return access.require(ctx(), group.id, (GroupMember member) -> {
            long id = Group.create(group, member.user);
            access.log(member, "Groups/add");
            return created(String.valueOf(id));
        });
    }

    public Result searchGroup(String name) {
        Restrict access = READ;
        return access.require(ctx(), -1, (GroupMember member) -> {
                    access.log(member, "Groups/search: " + name);
                    return ok(Json.toJson(Group.search(name, GroupMember.PERM_READ_PUBLIC))).as("application/json");
                });
    }

    public Result removeGroup(long id) {
        Restrict access = ROOT;
        return access.require(ctx(), id, (GroupMember member) -> {
            Group.delete(id);
            System.out.println("Removed group " + id + " (" + member.user.id + ")");
            return ok("Gone");
        });
    }

    public Result getUsers(long id) {
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), id, (GroupMember member) -> ok(Json.toJson(member.group.members)).as("application/json"));
    }

    public Result getWriteRequests(long id) {
        return MODIFY.require(ctx(), id,
                (GroupMember member) -> ok(Json.toJson(Group.get(id, member.permission).getWriteRequests())).as("application/json"));
    }

    public Result grantWriteAccess(long userId, long groupId) {
        Restrict access = MODIFY;
        return access.require(ctx(), groupId, (GroupMember mod) -> {
            GroupMember nonmember = GroupMember.get(userId, groupId);
            if(nonmember != null && nonmember.permission > mod.permission) return forbidden("Can't demote members");
            if (Group.grantWrite(userId, groupId)) {
                access.log(mod, "Groups/grantWrite, to: " + userId);
                return ok("Granted");
            } else {
                return internalServerError("Something went really wrong");
            }
        });
    }

    public Result grantModifyAccess(long userId, long groupId) {
        Restrict access = OWNER;
        return access.require(ctx(), groupId, (GroupMember owner) -> {
            GroupMember regularMember = GroupMember.get(userId, groupId);
            if(regularMember != null && regularMember.permission > owner.permission) return forbidden("Can't demote members");
            if (Group.grantModify(userId, groupId)) {
                access.log(owner, "Groups/grantMod, to: " + userId);
                return ok("Granted");
            } else {
                return internalServerError("Something went really wrong");
            }
        });
    }

    public Result grantOwner(long userId, long groupId) {
        Restrict access = OWNER;
        return access.require(ctx(), groupId, (GroupMember owner) -> {
            GroupMember modMember = GroupMember.get(userId, groupId);
            if(modMember != null && modMember.permission > owner.permission) return forbidden("Can't demote members");
            if(Group.grantOwner(userId, groupId)) {
                access.log(owner, "Groups/grantOwner");
                return ok("Granted");
            } else {
                return internalServerError("Something went really wrong");
            }
        });
    }

    public Result revokeWrite(long userId, long groupId) {
        Restrict access = OWNER;
        return access.require(ctx(), groupId, (GroupMember member) -> {
            if( Group.revokeWrite(userId, groupId)) {
                access.log(member, "Groups/kick, id: " + userId);
                return ok("Revoked");
            } else {
                return internalServerError("Something went really wrong");
            }
        });
    }

    public Result invite(long groupId) {
        Restrict access = WRITE;
        return access.require(ctx(), groupId, (GroupMember member) -> {
            Map<String, String[]> body = request().body().asFormUrlEncoded();
            if(body == null || !body.containsKey("email")) return badRequest("Bad request");
            String email = body.get("email")[0];
            boolean isApproved;
            isApproved = member.permission >= GroupMember.PERM_MODIFY;
            if(User.finder.where().eq("email", email).findRowCount() == 0) {
                Invitation.create(email, groupId, isApproved);
                //try {
                    //if(Emails.invite(member.user.username, member.group.name, email).getCode() >= 400)
                        return internalServerError();
                //} catch (SendGridException|JSONException e) {
                //    internalServerError();
                //}
            } else {
                Group.invite(groupId, User.byEmail(email), isApproved);
            }
            access.log(member, "Groups/invite");
            return ok("Invited");
        });
    }
}
