package com.kumusha.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import org.bson.types.ObjectId;

/**
 * Custom serializer for MongoDB's ObjectId to convert it to a string representation.
 *
 * <p>Documents in {@code sample_airbnb.listingsAndReviews} use string {@code _id} values rather
 * than ObjectIds, so this serializer rarely fires for listings themselves. It is registered
 * anyway so that any ObjectId reaching the JSON layer (for example from a raw aggregation
 * result or a document written by another tool) is rendered as a readable hex string instead
 * of Jackson's default base64 encoding.
 */
public class ObjectIdSerializer extends StdSerializer<ObjectId> {

    public ObjectIdSerializer() {
        super(ObjectId.class);
    }

    @Override
    public void serialize(ObjectId value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeString(value.toHexString());
    }
}
