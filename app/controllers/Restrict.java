package controllers;

import models.Group;
import models.GroupMember;
import models.User;
import play.mvc.Http;
import play.mvc.Result;

import java.util.function.Function;

/**
 * Created by luka on 6.1.16..
 */
public enum Restrict {

    @Deprecated
    READ(GroupMember.PERM_READ_PUBLIC) {
        @Override
        public Result require(Http.Context ctx, long groupId, Function<GroupMember, Result> action) {
            return super.require(ctx, IGNORE_GROUP, action);
        }
    },
    READ_PRESERVE_GROUP(GroupMember.PERM_READ_PUBLIC),
    WRITE(GroupMember.PERM_WRITE),
    MODIFY(GroupMember.PERM_MODIFY),
    OWNER(GroupMember.PERM_OWNER),
    ROOT(GroupMember.PERM_ROOT);

    public static final int IGNORE_GROUP = -1;
    protected final int requiredPermission;

    Restrict(int perm) {
        this.requiredPermission = perm;
    }

    public Result require(Http.Context ctx, long groupId, Function<GroupMember, Result> action) {
        String[] auth = ctx.request().headers().get("Authorization");
        String token = auth == null ? null : auth[0];
        try {
            long userId = User.verifyToken(token);

            if(groupId == IGNORE_GROUP) {
                return action.apply(new GroupMember(User.get(userId), null, GroupMember.PERM_READ_PUBLIC));
            } else {
                GroupMember member = GroupMember.get(userId, groupId);
                int permission = member == null ? GroupMember.PERM_READ_PUBLIC : member.permission;
                if(permission < requiredPermission) {
                    return User.handleInsufficientPermissions(permission);
                } else {
                    if(member == null) {
                        return action.apply(new GroupMember(User.get(userId),
                                Group.get(groupId, GroupMember.PERM_READ_PUBLIC),
                                GroupMember.PERM_READ_PUBLIC));
                    } else {
                        return action.apply(member);
                    }
                }
            }
        } catch (TokenException e) {
            return User.handleTokenException(e);
        }
    }

    public void log(GroupMember member, String action) {
        if(!Logging.log(ordinal(), member.user, member.group, action))
            System.err.println("Logging failed");
    }
}
