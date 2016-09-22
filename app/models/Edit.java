package models;

import com.avaje.ebean.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import play.data.validation.Constraints;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

/**
 * Created by luka on 26.12.15..
 */
@Entity
public class Edit extends Model {
    public static final int ACTION_EDIT_TEXT = 1;
    public static final int ACTION_REPLACE_IMAGE = 2;
    public static final int ACTION_ADD_IMAGE = 3;
    public static final int ACTION_REMOVE_IMAGE = 4;
    public static final int ACTION_CHANGE_LESSON = 5;
    public static final int ACTION_CHANGE_DATE = 6;
    public static final int ACTION_CHANGE_TYPE = 7;
    public static final int ACTION_ADD_AUDIO = 8;
    public static final int ACTION_REMOVE_AUDIO = 9;
    public static final int ACTION_REPLACE_AUDIO = 10;
    public static final int ACTION_CREATE = 11;


    public static Finder<Long, Edit> finder = new Finder<>(Edit.class);

    @JsonIgnore
    @Id
    public long id;

    @Constraints.Required
    @ManyToOne(cascade = CascadeType.ALL)
    public User editor;
    @Constraints.Required
    public int action;
    @Constraints.Required
    public long time;

    public Edit(User editor, int action, long time) {
        this.editor = editor;
        this.action = action;
        this.time = time;
    }

    public static Edit get(long id) {
        return finder.ref(id);
    }
}
