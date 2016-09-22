package models;

import play.api.mvc.Codec;
import play.mvc.Results;

/**
 * Created by luka on 2.1.16..
 */
public class DuplicateException extends Exception {
    public DuplicateException(String property) {
        super(property);
    }
    public Results.Status getResponse() {
        return new Results.Status(play.core.j.JavaResults.Conflict(), "Duplicate " + getMessage(), Codec.javaSupported("utf-8"));
    }
}
