package com.gnemirko.imdbvec.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

@Converter(autoApply = false)
public class PGVectorConverter implements AttributeConverter<float[], Object> {
    @Override
    public Object convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) return null;
        try {
            PGobject obj = new PGobject();
            obj.setType("vector");
            obj.setValue(toLiteral(attribute));
            return obj;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert embedding to PGobject", e);
        }
    }

    @Override
    public float[] convertToEntityAttribute(Object dbData) {
        if (dbData == null) return null;
        if (dbData instanceof float[] fa) return fa;
        if (dbData instanceof Double[] da) {
            float[] fa = new float[da.length];
            for (int i = 0; i < da.length; i++) fa[i] = da[i].floatValue();
            return fa;
        }
        String literal = dbData instanceof PGobject ? ((PGobject) dbData).getValue() : dbData.toString();
        return parseLiteral(literal);
    }

    private String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    private float[] parseLiteral(String literal) {
        if (literal == null || literal.isBlank()) return null;
        String trimmed = literal.trim();
        if (trimmed.length() < 2) return new float[0];
        if (trimmed.charAt(0) == '[' && trimmed.charAt(trimmed.length() - 1) == ']') {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) return new float[0];
        String[] parts = trimmed.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
    }
}
