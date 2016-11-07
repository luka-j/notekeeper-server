package models;

import com.avaje.ebean.Expr;
import com.fasterxml.jackson.annotation.JsonIgnore;
import play.data.validation.Constraints;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luka on 12.12.15..
 */
@Entity
@Table(name = "groups")
public class Group extends com.avaje.ebean.Model {
    @Constraints.Required
    @Column(columnDefinition = "VARCHAR(255)")
    public String name;
    @Column(columnDefinition = "VARCHAR(255)")
    public String place;
    @Id
    public long id;
    public boolean hasImage = false;

    @OneToMany(mappedBy = "group")
    @JsonIgnore
    public Set<GroupMember> members = new HashSet<>();
    @Lob
    public String courseYears = ","; //wannabe @ElementCollection
    @JsonIgnore
    public int requiredPermission = GroupMember.PERM_READ;
    public boolean inviteOnly = false;

    public static final int ALL_YEARS = -1;


    public String validate() {
        if(id != 0) return "Attempt to set id";
        if(hasImage) return "Attempt to set hasImage";
        if(!members.isEmpty()) return "Attempt to set members";
        if(!courseYears.equals(",")) return "Attempt to set courseYears";
        return null;
    }


    public static Group get(Long id, int permission) {
        Group g= finder.byId(id);
        if(g==null || g.requiredPermission > permission)
            return null;
        return g;
    }

    @JsonIgnore
    public Set<GroupMember> getWriteRequests() {
        return members.stream()
                .filter((GroupMember member) -> member.permission==GroupMember.PERM_REQUEST_WRITE)
                .collect(Collectors.toSet());
    }

    public static long create(Group group, User user) {
        GroupMember newMember = GroupMember.construct(user, group, GroupMember.PERM_CREATOR);
        group.members.add(newMember);
        group.save();
        user.groups.add(newMember);
        user.update();
        newMember.save();
        return group.id;
    }

    public static void invite(long groupId, User user, boolean isApproved) {
        Group g = get(groupId, GroupMember.PERM_WRITE);
        GroupMember member = GroupMember.construct(user, g, isApproved?GroupMember.PERM_WRITE:GroupMember.PERM_INVITED);
        g.members.add(member);
        g.update();
        member.save();
    }

    public boolean requestWrite(User user) {
        if(user == null) return false;
        if(inviteOnly) return false;
        GroupMember member = GroupMember.construct(user, this, GroupMember.PERM_REQUEST_WRITE);
        members.add(member);
        update();
        member.save();
        return true;
    }

    public static boolean revokeWrite(long userId, long groupId) {
        GroupMember member = GroupMember.get(userId, groupId);
        if(member == null) return false;
        member.permission = GroupMember.PERM_READ;
        member.update();
        Group group = finder.ref(groupId);
        group.members.remove(member);
        group.update();
        return true;
    }

    public static boolean grantWrite(long userId, long groupId) {
        return grantPermission(userId, finder.ref(groupId), GroupMember.PERM_WRITE);
    }

    public static boolean grantModify(long userId, long groupId) {
        return grantPermission(userId, finder.ref(groupId), GroupMember.PERM_MODIFY);
    }

    public static boolean grantOwner(long userId, long groupId) {
        return grantPermission(userId, finder.ref(groupId), GroupMember.PERM_OWNER);
    }

    private static boolean grantPermission(long userId, Group group, int permission) {
        GroupMember updated = GroupMember.get(userId, group.id);
        if(updated == null) return false;
        updated.permission = permission;
        boolean ok = !group.members.add(updated);
        //if(ok) {
            updated.update();
            return true;
        //} else return false;
    }

    public void delete() {
        super.delete();
        if(hasImage) {
            try {
                MediaUtils.removeImage("groups/" + id);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void delete(Long id) {
        finder.ref(id).delete();
    }

    public static void update(Long id, Group newGroup) {
        Group g = finder.ref(id);
        g.name = newGroup.name;
        g.place = newGroup.place;
        g.update();
    }

    public static void updateImage(Group group, File image) throws IOException, InterruptedException {
        group.hasImage = image != null;
        MediaUtils.updateImage(image, "groups/" + group.id, MediaUtils.MAX_THUMBNAIL_SIZE);
        group.update();
    }

    @Nullable
    public static File getImage(Long id, int size) throws IOException, InterruptedException {
        return MediaUtils.getImage("groups/" + id, size);
    }

    public static List<Group> search(String query, int permission) {
        return finder.where().and(Expr.icontains("name", query), Expr.le("requiredPermission", permission)).findList();
    }

    public static Finder<Long, Group> finder = new Finder<>(Group.class);

    public String toString() {
        return "ID: " + id + ", name: " + name + ", place: " + place;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Group && this.id == (((Group)obj).id);
    }
}
