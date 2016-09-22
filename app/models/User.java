package models;

import com.avaje.ebean.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;
import controllers.TokenException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.mindrot.jbcrypt.BCrypt;
import play.data.validation.Constraints;
import play.libs.Json;
import play.mvc.Result;
import play.mvc.Results;

import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by luka on 12.12.15..
 */
@Entity
@Table(name = "users")
public class User extends Model {
    protected static final long RESERVED_BITS = 7L << 61;
    private static final String JWT_HEADER = "{\n" +
            "  \"alg\": \"HS256\",\n" +
            "  \"typ\": \"JWT\"\n" +
            "}";
    public static final String JWT_SUBJECT = "sub";
    private static final String JWT_ISSUED_AT = "iat";
    private static final int  JWT_KEY_NUMBER = 2;
    private static final byte[] JWT_SECRET;

    static {
        try {
            JWT_SECRET = Hex.decodeHex("85421f7bf0ff6131c60b5fdfd5638b99c681661f05ac7c61d898a9e9fef6d812".toCharArray());
        } catch (DecoderException e) {
            throw new Error("Failed to decode hex for JWT key", e);
        }
    }

    public static final long TOKEN_REFRESH_PERIOD = 1000 * 60 * 20; //20min
    public static final long TOKEN_EXPIRED_PERIOD = 1000 * 60 * 60 * 24 * 7; //week

    public static Model.Finder<Long, User> finder = new Model.Finder<>(User.class);

    @Id
    public long id;
    @Constraints.Required
    @Constraints.Email
    @JsonIgnore
    @Column(unique = true)
    public String email;
    @Constraints.Required
    @Column(columnDefinition = "VARCHAR(255)")
    public String username;
    @JsonIgnore
    @Constraints.Required
    @Column(columnDefinition = "VARCHAR(512)")
    public String password;
    public boolean hasImage = false;
    @JsonIgnore
    @OneToMany(mappedBy = "user")
    public List<GroupMember> groups = new LinkedList<>();

    public static User get(long id) {
        return finder.ref(id);
    }
    public static User byEmail(String email) {
        return finder.where().eq("email", email).findUnique();
    }

    public static JsonNode verifyTokenPayload(String token) throws TokenException.Invalid {
        if(token == null) throw new TokenException.Invalid();
        String[] parts = token.split("\\.");
        char[] signature = signToken(parts[0] + "." + parts[1]);
        if(signature == null) throw new NullPointerException("Hashing token failed; signature == null");
        if(!String.valueOf(signature).equals(parts[2]))
            throw new TokenException.Invalid();
        String payload = new String(BaseEncoding.base64Url().decode(parts[1]), Charsets.UTF_8);
        JsonNode jsonPayload = Json.parse(payload);
        if(jsonPayload.size() != JWT_KEY_NUMBER ||
                !jsonPayload.hasNonNull(JWT_ISSUED_AT) ||
                !jsonPayload.hasNonNull(JWT_SUBJECT))
            throw new TokenException.Invalid();
        return jsonPayload;
    }

    public static long verifyToken(String token) throws TokenException {
        JsonNode jsonPayload = verifyTokenPayload(token);

        long issuedAt = jsonPayload.get(JWT_ISSUED_AT).asLong();
        if(System.currentTimeMillis() - issuedAt > TOKEN_REFRESH_PERIOD)
            throw new TokenException.Expired();
        if(System.currentTimeMillis() - issuedAt > TOKEN_EXPIRED_PERIOD)
            throw new TokenException.Invalid();
        long userId = jsonPayload.get(JWT_SUBJECT).asLong();
        return userId;
    }

    public static String register(User user) throws DuplicateException {
        if(finder.where().eq("email", user.email).findRowCount() > 0) {
            throw new DuplicateException("email");
        }
        String plain = user.password;
        user.password = BCrypt.hashpw(user.password, BCrypt.gensalt());
        user.save();
        return plain;
    }

    private static char[] signToken(String token) {
        try {
            SecretKeySpec key = new SecretKeySpec(JWT_SECRET, "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return (Hex.encodeHex(mac.doFinal(token.getBytes(Charsets.UTF_8))));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String generateToken() {
        StringBuilder token = new StringBuilder(192);
        token.append(BaseEncoding.base64Url().encode(JWT_HEADER.getBytes(Charsets.UTF_8)));
        token.append('.');
        ObjectNode payloadJson = Json.newObject().put(JWT_SUBJECT, id).put(JWT_ISSUED_AT, System.currentTimeMillis());
        token.append(BaseEncoding.base64Url().encode(payloadJson.toString().getBytes(Charsets.UTF_8)));
        char[] signature = signToken(token.toString());
        if(signature == null) throw new NullPointerException("Hashing token failed; signature == null");
        token.append('.');
        token.append(signature);
        return token.toString();
    }

    public static String login(String email, String password) {
        if(email == null || password == null) return null;
        User user = finder.where().eq("email", email).findUnique();
        if(user == null) return null;
        if(BCrypt.checkpw(password, user.password)) {
            return user.generateToken();
        }
        return null;
    }

    public GroupMember getGroupMember(long group) {
        if(group == -1) return null;
        for(GroupMember member : groups) {
            if(member.group.id == group) {
                return member;
            }
        }
        return null;
    }

    public static Result handleTokenException(TokenException ex) {
        if(ex instanceof TokenException.Expired) return Results.unauthorized("Expired");
        if(ex instanceof TokenException.Invalid) return Results.unauthorized("Invalid");
        throw new InvalidParameterException();
    }

    public static Result handleInsufficientPermissions(int permission) {
        switch (permission) {
            case GroupMember.PERM_READ_PUBLIC: return Results.forbidden("Inappropriate permissions (read)");
            case GroupMember.PERM_WRITE: return Results.forbidden("Inappropriate permissions (write)");
            case GroupMember.PERM_MODIFY: return Results.forbidden("Inappropriate permissions (modify)");
            default: return null;
        }
    }

    public static void updateImage(User user, File image) throws IOException, InterruptedException {
        user.hasImage = (image != null);
        MediaUtils.updateImage(image, "users/" + user.id, MediaUtils.MAX_THUMBNAIL_SIZE);
        user.update();
    }

    public void changePassword(String newPassword) {
        password = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        update();
    }

    @Nullable
    public static File getImage(long userId, int size) throws IOException, InterruptedException {
        return MediaUtils.getImage("users/" + userId, size);
    }

    @Override
    public String toString() {
        return username;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof User)) return false;
        User other = (User)obj;
        return (this.id == other.id)
                /*|| (obj instanceof Integer && (Integer)obj == id)*/;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

}