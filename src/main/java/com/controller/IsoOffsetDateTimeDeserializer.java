package com.controller;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// Only an ISO-8601 string with offset: Jackson's default would also take epoch numbers
public class IsoOffsetDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {

    static final String MESSAGE = "must be an ISO-8601 date-time with offset, e.g. 2026-09-24T08:12:49.515Z";

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (OffsetDateTime) context.handleUnexpectedToken(OffsetDateTime.class, parser);
        }
        String text = parser.getString();
        try {
            // ISO_OFFSET_DATE_TIME resolves strictly: no missing offset, no February 30
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw context.weirdStringException(text, OffsetDateTime.class, MESSAGE);
        }
    }
}
