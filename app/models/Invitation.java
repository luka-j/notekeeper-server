package models;

import com.avaje.ebean.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import play.data.validation.Constraints;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luka on 7.2.16..
 */
@Entity
public class Invitation extends Model {

    @Id
    public long id;
    @Column(unique = true)
    @Constraints.Email
    @JsonIgnore
    public String email;
    public String groups = ",";
    public boolean approved;

    public static final Finder<Long, Invitation> finder = new Finder<>(Invitation.class);

    private Invitation(String email, long groupId, boolean approved) {
        this.email = email;
        this.groups = "," + groupId + ",";
        this.approved = approved;
    }

    public static Invitation create(String email, long groupId, boolean isApproved) {
        Invitation inv;
        if(finder.where().eq("email", email).findRowCount() == 0) {
            inv = new Invitation(email, groupId, isApproved);
            inv.save();
        } else {
            inv = finder.where().eq("email", email).findUnique();
            if(!inv.groups.contains("," + groupId + ",")) {
                inv.groups = inv.groups.concat(groupId + ",");
                inv.update();
            }
        }
        return inv;
    }

    public static void approve(String email, boolean approve) { //todo approve invitations
        Invitation inv = finder.where().eq("email", email).findUnique();
        inv.approved = approve;
        inv.update();
    }

    public static Invitation get(String email) {
        return finder.where().eq("email", email).findUnique();
    }

    public Set<Group> getGroups() {
        return Arrays.stream(groups.split(","))
                .map((String groupId) -> Group.get(Long.parseLong(groupId), GroupMember.PERM_WRITE))
                .collect(Collectors.toSet());
    }

    public Set<Long> getGroupIds() {
        return Arrays.stream(groups.substring(1).split(",")).map(Long::parseLong).collect(Collectors.toSet());
    }
}
