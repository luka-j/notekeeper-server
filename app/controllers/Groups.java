package controllers;

import mails.Emails;
import models.*;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static controllers.Restrict.*;

/**
 * Created by luka on 11.12.15..
 */
public class Groups extends Controller {
    public static final int MAX_EMAILS_BULK = 100;
    public static final int INVITE_TIMEOUT = 3000;
    public static final int BULK_EMAIL_TIMEOUT = 5 * 60 * 1000;

    static Form<Group> groupForm = Form.form(Group.class);
    static Form<Announcement> announcementForm = Form.form(Announcement.class);

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
        Restrict access = READ_IGNORE_GROUP;
        return access.require(ctx(), group.id, (GroupMember member) -> {
            long id = Group.create(group, member.user);
            access.log(member, "Groups/add");
            return created(String.valueOf(id));
        });
    }

    public Result searchGroup(String name) {
        Restrict access = READ_IGNORE_GROUP;
        return access.require(ctx(), -1, (GroupMember member) -> {
                    access.log(member, "Groups/search: " + name);
                    return ok(Json.toJson(Group.search(name, GroupMember.PERM_READ))).as("application/json");
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

    public Result getUnreadAnnouncements(long groupId) {
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), groupId, (GroupMember member) -> { //allowing anyone to make this request
            List<Announcement> anns = member.fetchUnreadAnnouncements(); //access restriction done in GroupMember
            if(anns.isEmpty()) return status(304);
            else return ok(Json.toJson(anns));
        });
    }

    public Result getAllAnnouncements(long groupId) {
        return Restrict.WRITE.require(ctx(), groupId,
                (GroupMember member) -> ok(Json.toJson(Announcement.getAllAnnouncements(groupId))));
    }

    public Result addAnnouncement(long groupId) {
        Form<Announcement> filledForm = announcementForm.bindFromRequest();
        if(filledForm.hasErrors()) return badRequest("Form has errors");
        Announcement ann = filledForm.get();
        Restrict access = OWNER;
        return access.require(ctx(), groupId, (GroupMember member) -> {
            long id = Announcement.create(ann, member.group, member.user);
            access.log(member, "Announcements/add");
            return created(String.valueOf(id));
        });
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

    public Result approveInvitation(long groupId, String email) {
        Restrict access = MODIFY;
        return access.require(ctx(), groupId, (GroupMember member) -> {
            Invitation inv = Invitation.get(email);
            if(inv == null) return badRequest("There is no such invitation");
            inv.approve();
            access.log(member, "Groups/approveInvitation");
            return ok("approved");
        });
    }

    public Result invite(long groupId, String emails) {
        Restrict access = WRITE;
        return access.require(ctx(), groupId, (GroupMember member) -> {
            if(member.user.canSendNextMailTime < System.currentTimeMillis()) return unauthorized("Too many emails!");
            if(emails.contains(",")) {
                bulkInvite(groupId, emails.split("\\s+,\\s+"), member);
            } else {
                if(sendInvite(groupId, emails, member)) {
                    member.user.canSendNextMailTime = System.currentTimeMillis() + INVITE_TIMEOUT;
                    member.user.update();
                    return ok("invitation sent");
                } else {
                    return internalServerError("sending invitation failed");
                }
            }

            access.log(member, "Groups/invite");
            return ok("Invited");
        });
    }

    private Result bulkInvite(long groupId, String[] emails, GroupMember member) {
        if(emails.length > MAX_EMAILS_BULK) return badRequest("Too large bulk");
        int success = 0;
        for(String email : emails) {
            if(email.trim().isEmpty()) { success++; continue; }
            if(sendInvite(groupId, email, member)) success++;
        }
        if(success == 0) return internalServerError("email sending failed (all)");
        member.user.canSendNextMailTime = System.currentTimeMillis() + BULK_EMAIL_TIMEOUT;
        member.user.update();
        if(success == emails.length) return ok("sent all successfully");
        else return ok("sent " + success + "/" + emails.length);
    }

    private boolean sendInvite(long groupId, String email, GroupMember member) {
        boolean isApproved;
        isApproved = member.permission >= GroupMember.PERM_MODIFY;
        if(User.finder.where().eq("email", email).findRowCount() == 0) {
            Invitation.create(email, groupId, isApproved);
            try {
                Emails.invite(email, member.user.username, member.group.name);
            } catch (IOException e) {
                return false;
            }
        } else {
            Group.invite(groupId, User.byEmail(email), isApproved);
        }
        return true;
    }
}
